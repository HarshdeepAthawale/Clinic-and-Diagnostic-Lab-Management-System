package com.cdlms.consultation;

import com.cdlms.appointment.Appointment;
import com.cdlms.appointment.AppointmentDtos.StatusRequest;
import com.cdlms.appointment.AppointmentRepository;
import com.cdlms.appointment.AppointmentService;
import com.cdlms.appointment.AppointmentStatus;
import com.cdlms.auth.AuthUser;
import com.cdlms.billing.BillingService;
import com.cdlms.common.ApiException;
import com.cdlms.common.ClinicTime;
import com.cdlms.common.PageResponse;
import com.cdlms.consultation.ConsultationDtos.ConsultationRequest;
import com.cdlms.consultation.ConsultationDtos.ConsultationSummary;
import com.cdlms.consultation.ConsultationDtos.ConsultationView;
import com.cdlms.consultation.ConsultationDtos.DoctorBrief;
import com.cdlms.consultation.ConsultationDtos.FormularyItem;
import com.cdlms.consultation.ConsultationDtos.MedicineLine;
import com.cdlms.consultation.ConsultationDtos.PatientBrief;
import com.cdlms.consultation.ConsultationDtos.PrescriptionSummary;
import com.cdlms.consultation.ConsultationDtos.PrescriptionView;
import com.cdlms.consultation.ConsultationDtos.Vitals;
import com.cdlms.lab.LabOrder;
import com.cdlms.lab.LabOrderRepository;
import com.cdlms.patient.Patient;
import com.cdlms.patient.PatientAccessLog;
import com.cdlms.patient.PatientAccessLog.Resource;
import com.cdlms.patient.PatientAccessLogRepository;
import com.cdlms.patient.PatientRepository;
import com.cdlms.user.Doctor;
import com.cdlms.user.DoctorRepository;
import com.cdlms.user.Role;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Consultations and prescriptions (Docs/Rules.md §1b, ADR-021).
 *
 * <p>Workflow: the doctor starts a consultation on their own checked-in appointment (which calls the
 * patient in), saves drafts while the patient is in the room, then completes it — which issues the
 * prescription and completes the appointment in the same transaction.
 *
 * <p>Access: the author can always see and (while a draft) edit; other doctors can read completed
 * consultations of patients they have a care relationship with; patients read their own completed
 * consultations without the doctor's working notes. Every doctor read is written to the access log.
 */
@Service
public class ConsultationService {

    static final int HISTORY_LIMIT = 50;

    private final ConsultationRepository consultations;
    private final PrescriptionRepository prescriptions;
    private final ConsultationQueries queries;
    private final AppointmentRepository appointments;
    private final AppointmentService appointmentService;
    private final PatientRepository patients;
    private final DoctorRepository doctors;
    private final PatientAccessLogRepository accessLog;
    private final LabOrderRepository labOrders;
    private final BillingService billing;
    private final ClinicTime time;

    public ConsultationService(ConsultationRepository consultations, PrescriptionRepository prescriptions,
                               ConsultationQueries queries, AppointmentRepository appointments,
                               AppointmentService appointmentService, PatientRepository patients,
                               DoctorRepository doctors, PatientAccessLogRepository accessLog,
                               LabOrderRepository labOrders, BillingService billing, ClinicTime time) {
        this.consultations = consultations;
        this.prescriptions = prescriptions;
        this.queries = queries;
        this.appointments = appointments;
        this.appointmentService = appointmentService;
        this.patients = patients;
        this.doctors = doctors;
        this.accessLog = accessLog;
        this.labOrders = labOrders;
        this.billing = billing;
        this.time = time;
    }

    // ---------------------------------------------------------------- workflow (author doctor)

    /**
     * Opens the consultation for one of the doctor's appointments, creating it on first use. A
     * checked-in patient is called in first; a completed visit just opens its record.
     */
    @Transactional
    public ConsultationView start(AuthUser doctor, UUID appointmentId) {
        Doctor me = doctorOf(doctor);
        Appointment appointment = appointments.findById(appointmentId)
                .filter(a -> a.getDoctorId().equals(me.getId()))
                .orElseThrow(() -> notFound("Appointment not found"));

        Optional<Consultation> existing = consultations.findByAppointmentId(appointmentId);
        if (existing.isPresent()) {
            return view(existing.get(), true);
        }
        switch (appointment.getStatus()) {
            case CHECKED_IN -> appointmentService.changeStatus(doctor, appointmentId,
                    new StatusRequest(AppointmentStatus.IN_CONSULTATION, null));
            case IN_CONSULTATION -> {
            }
            default -> throw conflict("NOT_READY", appointment.getStatus() == AppointmentStatus.BOOKED
                    ? "The patient hasn't been checked in yet"
                    : "This visit has no consultation to open");
        }
        Consultation consultation = consultations.saveAndFlush(
                new Consultation(appointmentId, me.getId(), appointment.getPatientId()));
        return view(consultation, true);
    }

    @Transactional
    public ConsultationView saveDraft(AuthUser doctor, UUID id, ConsultationRequest request) {
        Consultation consultation = ownDraft(doctor, id);
        applyContent(consultation, request);
        return view(consultation, true);
    }

    /**
     * Final save + complete: requires a diagnosis; issues the prescription (if any medicines) with the
     * next RX number; completes the appointment. All or nothing.
     */
    @Transactional
    public ConsultationView complete(AuthUser doctor, UUID id, ConsultationRequest request) {
        Consultation consultation = ownDraft(doctor, id);
        applyContent(consultation, request);
        if (consultation.getDiagnosis() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "DIAGNOSIS_REQUIRED", "Add a diagnosis before finishing");
        }

        prescriptions.findByConsultationId(id).ifPresent(rx -> {
            if (rx.getItems().isEmpty()) {
                prescriptions.delete(rx);
            } else {
                rx.issue(prescriptions.nextCode(), time.now());
            }
            prescriptions.flush();
        });

        consultation.complete(time.now());
        consultations.saveAndFlush(consultation);

        Appointment appointment = appointments.findById(consultation.getAppointmentId()).orElseThrow();
        if (appointment.getStatus() == AppointmentStatus.IN_CONSULTATION) {
            appointmentService.changeStatus(doctor, appointment.getId(),
                    new StatusRequest(AppointmentStatus.COMPLETED, null));
        }

        // Bill the visit: the doctor's fee plus the tests ordered in it (ADR-023).
        billing.invoiceVisit(consultation,
                labOrders.findByConsultationIdAndStatus(consultation.getId(), LabOrder.Status.ORDERED), doctor.id());
        return view(consultation, true);
    }

    // ---------------------------------------------------------------- reads

    @Transactional
    public ConsultationView get(AuthUser caller, UUID id) {
        Consultation consultation = consultations.findById(id).orElseThrow(() -> notFound("Consultation not found"));
        switch (caller.role()) {
            case DOCTOR -> {
                Doctor me = doctorOf(caller);
                boolean author = consultation.getDoctorId().equals(me.getId());
                if (!author && (consultation.isDraft() || !hasCareRelationship(me, consultation.getPatientId()))) {
                    throw notFound("Consultation not found");
                }
                log(caller, consultation.getPatientId(), Resource.CONSULTATIONS, consultation.getId());
                return view(consultation, true);
            }
            case PATIENT -> {
                if (consultation.isDraft() || !ownPatientId(caller).equals(consultation.getPatientId())) {
                    throw notFound("Consultation not found");
                }
                return view(consultation, false);
            }
            default -> throw notFound("Consultation not found");
        }
    }

    /** A patient's completed visits: the patient themself, or a doctor with a care relationship (logged). */
    @Transactional
    public List<ConsultationSummary> historyForPatient(AuthUser caller, UUID patientId) {
        checkCanReadPatient(caller, patientId, Resource.CONSULTATIONS);
        return queries.completedForPatient(patientId, HISTORY_LIMIT);
    }

    @Transactional(readOnly = true)
    public PageResponse<ConsultationSummary> mine(AuthUser doctor, boolean todayOnly, int page, int size) {
        UUID doctorId = doctorOf(doctor).getId();
        Timestamp since = todayOnly ? Timestamp.from(time.startOf(time.today())) : null;
        return new PageResponse<>(queries.forDoctor(doctorId, since, size, page * size), page, size,
                queries.countForDoctor(doctorId, since));
    }

    @Transactional(readOnly = true)
    public List<PrescriptionSummary> myPrescriptions(AuthUser patient) {
        return queries.prescriptionsForPatient(ownPatientId(patient), HISTORY_LIMIT);
    }

    /** The consultation behind an issued prescription, checked like {@link #get} (for view and PDF). */
    @Transactional
    public ConsultationView prescription(AuthUser caller, UUID prescriptionId) {
        Prescription rx = prescriptions.findById(prescriptionId)
                .filter(Prescription::isIssued)
                .orElseThrow(() -> notFound("Prescription not found"));
        Consultation consultation = consultations.findById(rx.getConsultationId()).orElseThrow();
        switch (caller.role()) {
            case DOCTOR -> {
                Doctor me = doctorOf(caller);
                if (!consultation.getDoctorId().equals(me.getId())
                        && !hasCareRelationship(me, consultation.getPatientId())) {
                    throw notFound("Prescription not found");
                }
                log(caller, consultation.getPatientId(), Resource.PRESCRIPTION, rx.getId());
                return view(consultation, true);
            }
            case PATIENT -> {
                if (!ownPatientId(caller).equals(consultation.getPatientId())) {
                    throw notFound("Prescription not found");
                }
                return view(consultation, false);
            }
            default -> throw notFound("Prescription not found");
        }
    }

    @Transactional(readOnly = true)
    public List<FormularyItem> formulary(String query) {
        return query == null || query.isBlank() ? List.of() : queries.formulary(query, 12);
    }

    // ---------------------------------------------------------------- helpers

    private void applyContent(Consultation consultation, ConsultationRequest r) {
        Vitals v = r.vitals() == null ? Vitals.NONE : r.vitals();
        consultation.apply(new Consultation.Content(trim(r.chiefComplaint()), trim(r.notes()), trim(r.diagnosis()),
                trim(r.advice()), r.followUpDate(), toShort(v.bpSystolic()), toShort(v.bpDiastolic()),
                toShort(v.pulseBpm()), v.temperatureC(), toShort(v.spo2Percent()), v.weightKg()));
        consultations.saveAndFlush(consultation);

        List<MedicineLine> medicines = r.medicines() == null ? List.of() : r.medicines();
        Prescription rx = prescriptions.findByConsultationId(consultation.getId())
                .orElseGet(() -> new Prescription(consultation.getId()));
        // Remove old lines first so re-numbered positions never clash with the (id, position) key.
        rx.clearItems();
        prescriptions.saveAndFlush(rx);
        medicines.forEach(m -> rx.addItem(new PrescriptionItem.Line(m.medicine().trim(), trim(m.dosage()),
                m.frequency().trim(), m.duration().trim(), trim(m.instructions()))));
        prescriptions.saveAndFlush(rx);
    }

    private ConsultationView view(Consultation c, boolean includeNotes) {
        Patient p = patients.findById(c.getPatientId()).orElseThrow();
        Doctor d = doctors.findById(c.getDoctorId()).orElseThrow();
        Appointment a = appointments.findById(c.getAppointmentId()).orElseThrow();
        PrescriptionView rx = prescriptions.findByConsultationId(c.getId())
                .filter(r -> r.isIssued() || c.isDraft())
                .map(r -> new PrescriptionView(r.getId(), r.getPrescriptionCode(), r.getIssuedAt(),
                        r.getItems().stream().map(i -> {
                            PrescriptionItem.Line l = i.line();
                            return new MedicineLine(l.medicine(), l.dosage(), l.frequency(), l.duration(), l.instructions());
                        }).toList()))
                .orElse(null);
        return new ConsultationView(c.getId(), c.getAppointmentId(), c.getStatus(),
                a.getStartedAt() != null ? a.getStartedAt() : c.getCreatedAt(),
                new PatientBrief(p.getId(), p.getPatientCode(), p.getFullName(), p.ageOn(time.today()),
                        p.getGender().name(), p.getKnownAllergies(), p.getBloodGroup()),
                new DoctorBrief(d.getId(), d.getFullName(), d.getSpecialization(), d.getQualification(),
                        d.getRegistrationNumber()),
                c.getChiefComplaint(), includeNotes ? c.getNotes() : null, c.getDiagnosis(), c.getAdvice(),
                c.getFollowUpDate(),
                new Vitals(toInt(c.getBpSystolic()), toInt(c.getBpDiastolic()), toInt(c.getPulseBpm()),
                        c.getTemperatureC(), toInt(c.getSpo2Percent()), c.getWeightKg()),
                rx, c.getUpdatedAt(), c.getCompletedAt());
    }

    private Consultation ownDraft(AuthUser doctor, UUID id) {
        Consultation consultation = consultations.findById(id)
                .filter(c -> c.getDoctorId().equals(doctorOf(doctor).getId()))
                .orElseThrow(() -> notFound("Consultation not found"));
        if (!consultation.isDraft()) {
            throw conflict("CONSULTATION_LOCKED", "This consultation is finished and can no longer be changed");
        }
        return consultation;
    }

    private void checkCanReadPatient(AuthUser caller, UUID patientId, Resource resource) {
        switch (caller.role()) {
            case PATIENT -> {
                if (!ownPatientId(caller).equals(patientId)) {
                    throw forbidden();
                }
            }
            case DOCTOR -> {
                Doctor me = doctorOf(caller);
                if (!patients.existsById(patientId)) {
                    throw notFound("Patient not found");
                }
                if (!hasCareRelationship(me, patientId)) {
                    throw new ApiException(HttpStatus.FORBIDDEN, "NO_CARE_RELATIONSHIP",
                            "You don't have an appointment with this patient");
                }
                log(caller, patientId, resource, null);
            }
            default -> throw forbidden();
        }
    }

    private boolean hasCareRelationship(Doctor doctor, UUID patientId) {
        return patients.findWithCareRelationship(patientId, doctor.getId()).isPresent();
    }

    private void log(AuthUser caller, UUID patientId, Resource resource, UUID resourceId) {
        if (caller.role() == Role.DOCTOR || caller.role() == Role.PATHOLOGIST) {
            accessLog.save(new PatientAccessLog(patientId, caller.id(), resource, resourceId));
        }
    }

    private Doctor doctorOf(AuthUser doctor) {
        return doctors.findByUserId(doctor.id())
                .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "No doctor profile for this account"));
    }

    private UUID ownPatientId(AuthUser patient) {
        return patients.findByUserId(patient.id()).map(Patient::getId)
                .orElseThrow(() -> notFound("No patient record for this account"));
    }

    private static String trim(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static Short toShort(Integer value) {
        return value == null ? null : value.shortValue();
    }

    private static Integer toInt(Short value) {
        return value == null ? null : value.intValue();
    }

    private static ApiException notFound(String message) {
        return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", message);
    }

    private static ApiException forbidden() {
        return new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "You do not have access to this resource");
    }

    private static ApiException conflict(String code, String message) {
        return new ApiException(HttpStatus.CONFLICT, code, message);
    }
}
