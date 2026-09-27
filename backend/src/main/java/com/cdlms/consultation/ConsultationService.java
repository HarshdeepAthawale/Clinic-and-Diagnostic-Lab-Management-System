package com.cdlms.consultation;

import com.cdlms.appointment.Appointment;
import com.cdlms.appointment.AppointmentDtos.StatusRequest;
import com.cdlms.appointment.AppointmentRepository;
import com.cdlms.appointment.AppointmentService;
import com.cdlms.appointment.AppointmentStatus;
import com.cdlms.auth.AuthUser;
import com.cdlms.common.ApiException;
import com.cdlms.common.ClinicTime;
import com.cdlms.common.PageResponse;
import com.cdlms.consultation.ConsultationDtos.ConsultationSummary;
import com.cdlms.consultation.ConsultationDtos.ConsultationView;
import com.cdlms.consultation.ConsultationDtos.ItemRequest;
import com.cdlms.consultation.ConsultationDtos.MedicineSuggestion;
import com.cdlms.consultation.ConsultationDtos.PrescriptionRequest;
import com.cdlms.consultation.ConsultationDtos.PrescriptionView;
import com.cdlms.consultation.ConsultationDtos.ReviseRequest;
import com.cdlms.consultation.ConsultationDtos.UpdateConsultationRequest;
import com.cdlms.consultation.ConsultationDtos.Vitals;
import com.cdlms.patient.Patient;
import com.cdlms.patient.PatientAccessLog;
import com.cdlms.patient.PatientAccessLog.Resource;
import com.cdlms.patient.PatientAccessLogRepository;
import com.cdlms.patient.PatientRepository;
import com.cdlms.user.Doctor;
import com.cdlms.user.DoctorRepository;
import com.cdlms.user.Role;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

/**
 * Consultations and e-prescriptions (Docs/Rules.md §1b, ADR-021):
 * <ul>
 *   <li>The doctor of an appointment opens its consultation once the patient is called in, and edits
 *       it until they finish; finishing needs a diagnosis, locks it and completes the appointment.</li>
 *   <li>Prescriptions are immutable. A correction is a new prescription replacing the old one, with a
 *       reason; the old one stays on record.</li>
 *   <li>Doctors read a patient's consultations and prescriptions only with a care relationship, and
 *       every such read is written to the access log. Patients read their own, without the doctor's
 *       clinical notes. The front desk and admin never see clinical content.</li>
 * </ul>
 */
@Service
public class ConsultationService {

    private static final int MAX_PAGE_SIZE = 50;

    private final ConsultationRepository consultations;
    private final PrescriptionRepository prescriptions;
    private final ConsultationQueries queries;
    private final AppointmentRepository appointments;
    private final AppointmentService appointmentService;
    private final PatientRepository patients;
    private final PatientAccessLogRepository accessLog;
    private final DoctorRepository doctors;
    private final ClinicTime time;

    public ConsultationService(ConsultationRepository consultations, PrescriptionRepository prescriptions,
                               ConsultationQueries queries, AppointmentRepository appointments,
                               AppointmentService appointmentService, PatientRepository patients,
                               PatientAccessLogRepository accessLog, DoctorRepository doctors, ClinicTime time) {
        this.consultations = consultations;
        this.prescriptions = prescriptions;
        this.queries = queries;
        this.appointments = appointments;
        this.appointmentService = appointmentService;
        this.patients = patients;
        this.accessLog = accessLog;
        this.doctors = doctors;
        this.time = time;
    }

    // ---------------------------------------------------------------- consultation lifecycle

    /** Opens (or returns) the consultation for the doctor's own appointment. Idempotent. */
    @Transactional
    public ConsultationView open(AuthUser doctor, UUID appointmentId) {
        Appointment appointment = appointments.findById(appointmentId)
                .filter(a -> a.getDoctorId().equals(doctorIdOf(doctor)))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Appointment not found"));
        Consultation consultation = consultations.findByAppointmentId(appointmentId).orElseGet(() -> {
            if (appointment.getStatus() != AppointmentStatus.IN_CONSULTATION
                    && appointment.getStatus() != AppointmentStatus.COMPLETED) {
                throw conflict("NOT_IN_CONSULTATION", "Call the patient in before starting the consultation");
            }
            return consultations.saveAndFlush(new Consultation(appointmentId, appointment.getPatientId(),
                    appointment.getDoctorId(), appointment.getReason()));
        });
        return view(consultation, true);
    }

    @Transactional
    public ConsultationView get(AuthUser caller, UUID id) {
        Consultation consultation = find(id);
        switch (caller.role()) {
            case DOCTOR -> {
                requireCare(caller, consultation.getPatientId());
                log(caller, consultation.getPatientId(), Resource.CONSULTATIONS, consultation.getId());
                return view(consultation, true);
            }
            case PATIENT -> {
                requireOwn(caller, consultation.getPatientId());
                return view(consultation, false);
            }
            default -> throw notFound();
        }
    }

    @Transactional
    public ConsultationView update(AuthUser doctor, UUID id, UpdateConsultationRequest request) {
        Consultation consultation = ownOpen(doctor, id);
        consultation.update(clean(request.chiefComplaint()), clean(request.notes()), clean(request.diagnosis()),
                clean(request.advice()), request.followUpDate(),
                request.vitals() == null ? Vitals.EMPTY.toEntity() : request.vitals().toEntity());
        return view(consultations.saveAndFlush(consultation), true);
    }

    /** Finishes the visit: needs a diagnosis, locks the notes and completes the appointment. */
    @Transactional
    public ConsultationView complete(AuthUser doctor, UUID id) {
        Consultation consultation = ownOpen(doctor, id);
        if (consultation.getDiagnosis() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "DIAGNOSIS_REQUIRED", "Add a diagnosis before finishing");
        }
        Appointment appointment = appointments.findById(consultation.getAppointmentId()).orElseThrow();
        if (appointment.getStatus() == AppointmentStatus.IN_CONSULTATION) {
            appointmentService.changeStatus(doctor, appointment.getId(),
                    new StatusRequest(AppointmentStatus.COMPLETED, null));
        }
        consultation.complete(time.now());
        return view(consultations.saveAndFlush(consultation), true);
    }

    // ---------------------------------------------------------------- prescriptions

    @Transactional
    public PrescriptionView prescribe(AuthUser doctor, UUID consultationId, PrescriptionRequest request) {
        Consultation consultation = own(doctor, consultationId);
        if (prescriptions.existsByConsultationIdAndReplacesIdIsNull(consultationId)) {
            throw conflict("ALREADY_PRESCRIBED", "This visit already has a prescription. Revise it instead.");
        }
        Prescription rx = prescriptions.saveAndFlush(new Prescription(consultation, clean(request.advice()),
                items(request.items()), null, null, time.now()));
        return queries.prescription(rx.getId()).orElseThrow();
    }

    /** Issues a corrected prescription that replaces {@code prescriptionId}; the original is kept. */
    @Transactional
    public PrescriptionView revise(AuthUser doctor, UUID prescriptionId, ReviseRequest request) {
        Prescription original = prescriptions.findById(prescriptionId)
                .filter(rx -> rx.getDoctorId().equals(doctorIdOf(doctor)))
                .orElseThrow(ConsultationService::prescriptionNotFound);
        if (prescriptions.existsByReplacesId(prescriptionId)) {
            throw conflict("ALREADY_REVISED", "This prescription was already replaced. Revise the newest one.");
        }
        Consultation consultation = find(original.getConsultationId());
        Prescription rx = prescriptions.saveAndFlush(new Prescription(consultation, clean(request.advice()),
                items(request.items()), prescriptionId, request.reason().trim(), time.now()));
        return queries.prescription(rx.getId()).orElseThrow();
    }

    @Transactional
    public PrescriptionView prescription(AuthUser caller, UUID id) {
        PrescriptionView rx = queries.prescription(id).orElseThrow(ConsultationService::prescriptionNotFound);
        switch (caller.role()) {
            case DOCTOR -> {
                requireCare(caller, rx.patient().id());
                log(caller, rx.patient().id(), Resource.PRESCRIPTION, id);
            }
            case PATIENT -> requireOwn(caller, rx.patient().id());
            default -> throw prescriptionNotFound();
        }
        return rx;
    }

    // ---------------------------------------------------------------- histories

    /** A patient's visit history: the patient themself, or a doctor with a care relationship (logged). */
    @Transactional
    public List<ConsultationSummary> forPatient(AuthUser caller, UUID patientId) {
        switch (caller.role()) {
            case DOCTOR -> {
                requireCare(caller, patientId);
                log(caller, patientId, Resource.CONSULTATIONS, null);
            }
            case PATIENT -> requireOwn(caller, patientId);
            default -> throw notFound();
        }
        return queries.forPatient(patientId, 100);
    }

    /** Doctor: the consultations they wrote. Patient: their own visits. */
    @Transactional(readOnly = true)
    public PageResponse<ConsultationSummary> mine(AuthUser caller, int page, int size) {
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, MAX_PAGE_SIZE));
        if (caller.role() == Role.PATIENT) {
            List<ConsultationSummary> rows = queries.forPatient(ownPatient(caller).getId(), 100);
            return new PageResponse<>(rows, 0, rows.size(), rows.size());
        }
        UUID doctorId = doctorIdOf(caller);
        List<ConsultationSummary> rows = queries.forDoctor(doctorId, (int) pageable.getOffset(), pageable.getPageSize());
        return PageResponse.of(new PageImpl<>(rows, pageable, queries.countForDoctor(doctorId)), r -> r);
    }

    @Transactional(readOnly = true)
    public List<PrescriptionView> myPrescriptions(AuthUser patient) {
        return queries.prescriptionsForPatient(ownPatient(patient).getId(), 100);
    }

    @Transactional(readOnly = true)
    public List<MedicineSuggestion> medicines(String query) {
        String q = query == null ? "" : query.trim();
        return q.length() < 2 ? List.of() : queries.medicines(q, 8);
    }

    // ---------------------------------------------------------------- helpers

    private ConsultationView view(Consultation c, boolean withNotes) {
        return new ConsultationView(c.getId(), c.getAppointmentId(), c.isCompleted(),
                queries.patientRef(c.getPatientId()), queries.doctorCard(c.getDoctorId()), c.getChiefComplaint(),
                withNotes ? c.getNotes() : null, c.getDiagnosis(), c.getAdvice(), c.getFollowUpDate(),
                Vitals.of(c.getVitals()), c.getCreatedAt(), c.getUpdatedAt(), c.getCompletedAt(),
                queries.prescriptionsForConsultation(c.getId()));
    }

    private static List<PrescriptionItem> items(List<ItemRequest> items) {
        return IntStream.range(0, items.size()).mapToObj(i -> {
            ItemRequest it = items.get(i);
            return new PrescriptionItem(i + 1, it.medicine().trim(), clean(it.dose()), it.frequency().trim(),
                    it.durationDays(), clean(it.instructions()));
        }).toList();
    }

    /** The calling doctor's own consultation, in any state. */
    private Consultation own(AuthUser doctor, UUID id) {
        Consultation consultation = find(id);
        if (!consultation.getDoctorId().equals(doctorIdOf(doctor))) {
            throw notFound();
        }
        return consultation;
    }

    /** The calling doctor's own consultation, still editable. */
    private Consultation ownOpen(AuthUser doctor, UUID id) {
        Consultation consultation = own(doctor, id);
        if (consultation.isCompleted()) {
            throw conflict("CONSULTATION_COMPLETED", "This consultation is finished and can no longer be edited");
        }
        return consultation;
    }

    private Consultation find(UUID id) {
        return consultations.findById(id).orElseThrow(ConsultationService::notFound);
    }

    private void requireCare(AuthUser doctor, UUID patientId) {
        if (!patients.hasCareRelationship(patientId, doctorIdOf(doctor))) {
            throw new ApiException(HttpStatus.FORBIDDEN, "NO_CARE_RELATIONSHIP",
                    "You don't have an appointment with this patient");
        }
    }

    private void requireOwn(AuthUser patient, UUID patientId) {
        if (!ownPatient(patient).getId().equals(patientId)) {
            throw notFound();
        }
    }

    private void log(AuthUser caller, UUID patientId, Resource resource, UUID resourceId) {
        accessLog.save(new PatientAccessLog(patientId, caller.id(), resource, resourceId));
    }

    private Patient ownPatient(AuthUser caller) {
        return patients.findByUserId(caller.id())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "No patient record for this account"));
    }

    private UUID doctorIdOf(AuthUser doctor) {
        return doctors.findByUserId(doctor.id()).map(Doctor::getId)
                .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "No doctor profile for this account"));
    }

    private static String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Consultation not found");
    }

    private static ApiException prescriptionNotFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Prescription not found");
    }

    private static ApiException conflict(String code, String message) {
        return new ApiException(HttpStatus.CONFLICT, code, message);
    }
}
