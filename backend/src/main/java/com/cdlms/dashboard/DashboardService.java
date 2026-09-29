package com.cdlms.dashboard;

import com.cdlms.appointment.AppointmentDtos.AppointmentView;
import com.cdlms.appointment.AppointmentQueries;
import com.cdlms.appointment.AppointmentService;
import com.cdlms.appointment.AppointmentStatus;
import com.cdlms.auth.AuthUser;
import com.cdlms.billing.BillingDtos.InvoiceSummary;
import com.cdlms.billing.BillingQueries;
import com.cdlms.common.ApiException;
import com.cdlms.consultation.ConsultationQueries;
import com.cdlms.dashboard.DashboardQueries.Window;
import com.cdlms.dashboard.Widget.Stat;
import com.cdlms.result.ResultDtos.ReportRow;
import com.cdlms.result.ResultQueries;
import com.cdlms.user.Pathologist;
import com.cdlms.user.PathologistRepository;
import com.cdlms.lab.LabDtos.LabOrderView;
import com.cdlms.lab.LabOrderService;
import com.cdlms.lab.LabQueries;
import com.cdlms.patient.Patient;
import com.cdlms.sample.NotificationService;
import com.cdlms.sample.SampleQueries;
import com.cdlms.sample.SampleStatus;
import com.cdlms.patient.PatientRepository;
import com.cdlms.user.Doctor;
import com.cdlms.user.DoctorRepository;
import com.cdlms.user.Role;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Builds each role's dashboard from real data only: a list of {@link Widget}s, each backed by one
 * query in {@link DashboardQueries}. Modules that don't exist yet appear as an "upcoming" list, not
 * as numbers. To extend a dashboard in a later phase, add a query and a widget here.
 */
@Service
public class DashboardService {

    private final DashboardQueries queries;
    private final DoctorRepository doctors;
    private final PatientRepository patients;
    private final AppointmentService appointments;
    private final AppointmentQueries appointmentQueries;
    private final ConsultationQueries consultationQueries;
    private final LabQueries labQueries;
    private final LabOrderService labOrders;
    private final BillingQueries billingQueries;
    private final SampleQueries sampleQueries;
    private final NotificationService notifications;
    private final ResultQueries resultQueries;
    private final PathologistRepository pathologists;
    private final ZoneId zone;

    public DashboardService(DashboardQueries queries, DoctorRepository doctors, PatientRepository patients,
                            AppointmentService appointments, AppointmentQueries appointmentQueries,
                            ConsultationQueries consultationQueries, LabQueries labQueries,
                            LabOrderService labOrders, BillingQueries billingQueries,
                            SampleQueries sampleQueries, NotificationService notifications,
                            ResultQueries resultQueries, PathologistRepository pathologists,
                            @Value("${app.clinic.zone:Asia/Kolkata}") String zone) {
        this.queries = queries;
        this.doctors = doctors;
        this.patients = patients;
        this.appointments = appointments;
        this.appointmentQueries = appointmentQueries;
        this.consultationQueries = consultationQueries;
        this.labQueries = labQueries;
        this.labOrders = labOrders;
        this.billingQueries = billingQueries;
        this.sampleQueries = sampleQueries;
        this.notifications = notifications;
        this.resultQueries = resultQueries;
        this.pathologists = pathologists;
        this.zone = ZoneId.of(zone);
    }

    public record DashboardResponse(Role role, List<Widget> widgets) {
    }

    @Transactional(readOnly = true)
    public DashboardResponse forUser(AuthUser user) {
        List<Widget> widgets = switch (user.role()) {
            case DOCTOR -> doctor(user);
            case RECEPTIONIST -> reception(user);
            case ADMIN -> admin(user);
            case PATIENT -> patient(user);
            case PATHOLOGIST -> pathologist(user);
            case LAB_TECHNICIAN -> lab();
        };
        return new DashboardResponse(user.role(), widgets);
    }

    // ------------------------------------------------------------------ roles

    private List<Widget> doctor(AuthUser user) {
        UUID doctorId = doctors.findByUserId(user.id()).map(Doctor::getId)
                .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "No doctor profile for this account"));
        Window today = today();
        LocalDate date = LocalDate.now(zone);
        List<Widget> widgets = new ArrayList<>();
        queries.openConsultation(doctorId)
                .ifPresent(open -> widgets.add(new Widget("openConsultation", "In consultation", "full", open)));
        List<ReportRow> ready = resultQueries.orderedBy(doctorId, 5, 0);
        widgets.addAll(List.of(
                Widget.stats(List.of(
                        new Stat("appointmentsToday", "Appointments today", queries.appointmentsBetween(today, doctorId), null),
                        new Stat("seenToday", "Seen today", queries.appointmentsWithStatus(today, doctorId, "COMPLETED"), null),
                        new Stat("waiting", "Checked in, waiting", queries.appointmentsWithStatus(today, doctorId, "CHECKED_IN"), null),
                        new Stat("underCare", "Patients under your care", queries.patientsUnderCare(doctorId), null))),
                new Widget("liveQueue", "Your queue", "wide", appointments.queue(user)),
                new Widget("recentRecords", "Records you opened", "narrow", queries.recentAccess(user.id(), 6)),
                new Widget("schedule", "Today's schedule", "full",
                        queries.schedule(today, doctorId).stream().map(e -> scheduleRow(e, date)).toList())));
        if (!ready.isEmpty()) {
            widgets.add(new Widget("reportsReady", "Lab reports for your patients", "full", ready));
        }
        return widgets;
    }

    /** The lab's day: samples to draw and to check in (urgent and redraws first), and which tubes to set out. */
    private List<Widget> lab() {
        Window today = today();
        return List.of(
                Widget.stats(List.of(
                        new Stat("samplesToCollect", "To collect", sampleQueries.countWaiting(SampleStatus.ORDERED),
                                sampleQueries.countUrgentToCollect() + " urgent"),
                        new Stat("samplesToReceive", "To receive", sampleQueries.countWaiting(SampleStatus.COLLECTED),
                                "Drawn, awaiting the receipt check"),
                        new Stat("samplesToTest", "To test", resultQueries.countToTest(),
                                resultQueries.countReturned() + " returned for retest"),
                        new Stat("reportsToSend", "Reports to send", resultQueries.countToDispatch(),
                                "Verified, not yet sent to the patient"))),
                new Widget("sampleQueue", "Sample bench", "wide", Map.of(
                        "toCollect", sampleQueries.waiting(SampleStatus.ORDERED, 5, 0),
                        "collectTotal", sampleQueries.countWaiting(SampleStatus.ORDERED),
                        "toReceive", sampleQueries.waiting(SampleStatus.COLLECTED, 5, 0),
                        "receiveTotal", sampleQueries.countWaiting(SampleStatus.COLLECTED))),
                new Widget("tubesNeeded", "Tubes to set out", "narrow", sampleQueries.tubesToCollect()),
                new Widget("testQueue", "To test", "wide", Map.of(
                        "samples", resultQueries.toTest(5, 0), "total", resultQueries.countToTest())),
                new Widget("dispatchQueue", "Reports to send", "narrow", Map.of(
                        "reports", resultQueries.toDispatch(5, 0), "total", resultQueries.countToDispatch())));
    }

    /** The pathologist's day: what is waiting for sign-off (critical first) and what they have done today. */
    private List<Widget> pathologist(AuthUser user) {
        UUID pathologistId = pathologists.findByUserId(user.id()).map(Pathologist::getId)
                .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "No pathologist profile for this account"));
        Window today = today();
        long waiting = resultQueries.countPendingVerification();
        return List.of(
                Widget.stats(List.of(
                        new Stat("awaitingVerification", "Awaiting verification", waiting, "Results to sign off"),
                        new Stat("criticalWaiting", "Critical", resultQueries.countPendingCritical(), "Waiting with a critical value"),
                        new Stat("verifiedToday", "Verified today",
                                resultQueries.countVerifiedBetween(pathologistId, today.from(), today.to()), null),
                        new Stat("returnedToday", "Returned today",
                                resultQueries.countReturnedBetween(pathologistId, today.from(), today.to()), "Sent back for retest"))),
                new Widget("verificationQueue", "Verification queue", "full", Map.of(
                        "results", resultQueries.pendingVerification(6, 0), "total", waiting)));
    }

    private List<Widget> reception(AuthUser user) {
        Window today = today();
        LocalDate date = LocalDate.now(zone);
        List<Widget> widgets = new ArrayList<>(receptionWidgets(user, today, date));
        var alerts = notifications.open(user);
        if (alerts.open() > 0) {
            // Redraws to arrange are time-sensitive (a patient has to be called back): right after the tiles.
            widgets.add(1, new Widget("sampleAlerts", "Patients to call back", "full", alerts));
        }
        return widgets;
    }

    private List<Widget> receptionWidgets(AuthUser user, Window today, LocalDate date) {
        return List.of(
                Widget.stats(List.of(
                        new Stat("appointmentsToday", "Appointments today", queries.appointmentsBetween(today, null), null),
                        new Stat("waiting", "Waiting now", queries.appointmentsWithStatus(today, null, "CHECKED_IN"),
                                "Checked in, not yet called"),
                        new Stat("registeredToday", "Registered today", queries.patientsRegisteredBetween(today), null),
                        new Stat("pendingCodes", "Waiting to link their login", queries.pendingRegistrationCodes(Instant.now()),
                                "Registration codes not used yet"))),
                new Widget("liveQueue", "Live queue", "wide", appointments.queue(user)),
                new Widget("recentPatients", "Recently registered", "narrow",
                        queries.recentPatients(8).stream().map(p -> Map.of(
                                "id", p.id(), "patientCode", p.patientCode(), "fullName", p.fullName(),
                                "age", Period.between(p.dob(), date).getYears(), "gender", p.gender(),
                                "hasLogin", p.hasLogin(), "registeredAt", p.registeredAt())).toList()),
                new Widget("schedule", "Today's appointments", "full",
                        queries.schedule(today, null).stream().map(e -> scheduleRow(e, date)).toList()),
                billingCounter(),
                new Widget("collections", "Collected today", "narrow",
                        billingQueries.collectedBetween(today.from(), today.to())));
    }

    /** Unpaid and part-paid bills, oldest first, with what's owed in total. */
    private Widget billingCounter() {
        return new Widget("outstandingBills", "Bills to collect", "wide", Map.of(
                "invoices", billingQueries.list(BillingQueries.Filter.OUTSTANDING, "", 6, 0),
                "count", billingQueries.countOutstanding(),
                "amount", billingQueries.outstandingAmount()));
    }

    /** Clinic-wide overview: patients, today's visits by status, the live queue, record access, accounts. */
    private List<Widget> admin(AuthUser user) {
        Window today = today();
        return List.of(
                Widget.stats(List.of(
                        new Stat("totalPatients", "Patients on record", queries.totalPatients(), null),
                        new Stat("appointmentsToday", "Appointments today", queries.appointmentsBetween(today, null), null),
                        new Stat("registeredToday", "Registered today", queries.patientsRegisteredBetween(today), null),
                        new Stat("recordOpensToday", "Record opens today", queries.recordOpensBetween(today, null), null))),
                new Widget("liveQueue", "Live queue", "wide", appointments.queue(user)),
                new Widget("visitsByStatus", "Today's visits", "narrow",
                        appointmentQueries.statusCounts(today.from(), today.to())),
                new Widget("accessLog", "Record access log", "wide", queries.recentAccess(null, 8)),
                new Widget("team", "Accounts by role", "narrow", queries.activeUsersByRole()),
                billingCounter(),
                new Widget("collections", "Collected today", "narrow",
                        billingQueries.collectedBetween(today.from(), today.to())));
    }

    private List<Widget> patient(AuthUser user) {
        Patient patient = patients.findByUserId(user.id())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "No patient record for this account"));
        List<Widget> widgets = new ArrayList<>();
        myQueueSpot(patient.getId()).ifPresent(spot -> widgets.add(new Widget("myQueue", "You're checked in", "full", spot)));
        widgets.addAll(List.of(
                new Widget("myRecord", "Your record", "narrow", Map.of(
                        "patientCode", patient.getPatientCode(),
                        "bloodGroup", nullable(patient.getBloodGroup()),
                        "knownAllergies", nullable(patient.getKnownAllergies()),
                        "hasHistory", patient.getMedicalHistory() != null,
                        "updatedAt", patient.getUpdatedAt())),
                new Widget("upcomingAppointments", "Upcoming appointments", "wide",
                        queries.upcomingForPatient(patient.getId(), Instant.now(), 5)),
                new Widget("recentPrescriptions", "Recent prescriptions", "full",
                        consultationQueries.prescriptionsForPatient(patient.getId(), 4))));
        List<LabOrderView> tests = labOrders.openForPatient(patient.getId());
        if (!tests.isEmpty()) {
            // Tests to get done come right after the queue card: prep (fasting etc.) is time-sensitive.
            int at = widgets.getFirst().type().equals("myQueue") ? 1 : 0;
            widgets.add(at, new Widget("myLabOrders", "Tests to get done", "full", tests));
        }
        List<ReportRow> reportRows = resultQueries.dispatchedForPatient(patient.getId(), 3);
        if (!reportRows.isEmpty()) {
            // New results matter more than the rest of the page: right after the queue card and any tests to do.
            int at = widgets.stream().takeWhile(w -> w.type().equals("myQueue") || w.type().equals("myLabOrders")).toList().size();
            widgets.add(at, new Widget("myReports", "Your reports", "full", reportRows));
        }
        List<InvoiceSummary> bills = billingQueries.outstandingForPatient(patient.getId(), 3);
        if (!bills.isEmpty()) {
            widgets.add(new Widget("myBills", "Bills to pay", "full", bills));
        }
        return widgets;
    }

    /**
     * The patient's live place in today's queue: their token, the token being seen now and how many
     * are ahead. Empty unless they are checked in or with the doctor.
     */
    private Optional<Map<String, Object>> myQueueSpot(UUID patientId) {
        List<AppointmentView> queue = appointmentQueries.queue(LocalDate.now(zone), null);
        return queue.stream()
                .filter(a -> a.patient().id().equals(patientId))
                .filter(a -> a.status() == AppointmentStatus.CHECKED_IN || a.status() == AppointmentStatus.IN_CONSULTATION)
                .findFirst()
                .map(mine -> {
                    List<AppointmentView> sameDoctor = queue.stream()
                            .filter(a -> a.doctor().id().equals(mine.doctor().id())).toList();
                    long ahead = sameDoctor.stream().filter(a -> a.status() == AppointmentStatus.CHECKED_IN
                            && a.checkedInAt().isBefore(mine.checkedInAt())).count();
                    String nowServing = sameDoctor.stream()
                            .filter(a -> a.status() == AppointmentStatus.IN_CONSULTATION)
                            .map(AppointmentView::queueToken).findFirst().orElse("");
                    return Map.of("token", mine.queueToken(), "status", mine.status(), "doctorName",
                            mine.doctor().fullName(), "specialization", mine.doctor().specialization(),
                            "ahead", ahead, "nowServing", nowServing);
                });
    }

    // ------------------------------------------------------------------ helpers

    private Window today() {
        LocalDate date = LocalDate.now(zone);
        return new Window(date.atStartOfDay(zone).toInstant(), date.plusDays(1).atStartOfDay(zone).toInstant());
    }

    private static Map<String, Object> scheduleRow(DashboardQueries.ScheduleEntry e, LocalDate date) {
        return Map.of("appointmentId", e.appointmentId(), "scheduledAt", e.scheduledAt(), "status", e.status(),
                "queueToken", nullable(e.queueToken()), "doctorName", e.doctorName(),
                "patient", Map.of("id", e.patientId(), "patientCode", e.patientCode(), "fullName", e.patientName(),
                        "age", Period.between(e.dob(), date).getYears(), "gender", e.gender()));
    }

    /** Map.of rejects nulls; the frontend treats "" as "not recorded". */
    private static Object nullable(Object value) {
        return value == null ? "" : value;
    }
}
