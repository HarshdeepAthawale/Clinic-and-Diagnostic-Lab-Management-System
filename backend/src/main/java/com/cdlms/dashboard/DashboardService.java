package com.cdlms.dashboard;

import com.cdlms.auth.AuthUser;
import com.cdlms.common.ApiException;
import com.cdlms.dashboard.DashboardQueries.Window;
import com.cdlms.dashboard.Widget.Stat;
import com.cdlms.dashboard.Widget.Upcoming;
import com.cdlms.patient.Patient;
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
import java.util.List;
import java.util.Map;
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
    private final ZoneId zone;

    public DashboardService(DashboardQueries queries, DoctorRepository doctors, PatientRepository patients,
                            @Value("${app.clinic.zone:Asia/Kolkata}") String zone) {
        this.queries = queries;
        this.doctors = doctors;
        this.patients = patients;
        this.zone = ZoneId.of(zone);
    }

    public record DashboardResponse(Role role, List<Widget> widgets) {
    }

    @Transactional(readOnly = true)
    public DashboardResponse forUser(AuthUser user) {
        List<Widget> widgets = switch (user.role()) {
            case DOCTOR -> doctor(user);
            case RECEPTIONIST -> reception();
            case ADMIN -> admin();
            case PATIENT -> patient(user);
            case PATHOLOGIST -> List.of(Widget.upcoming("Your workspace", List.of(
                    new Upcoming("Verification queue", 8, "Results waiting for your sign-off, critical values first."),
                    new Upcoming("Focus mode", 8, "Review one result at a time and sign off from the keyboard."),
                    new Upcoming("Return for retest", 8, "Send doubtful results back to the bench with a reason."))));
            case LAB_TECHNICIAN -> List.of(Widget.upcoming("Your workspace", List.of(
                    new Upcoming("Test orders", 5, "Orders arrive here the moment a doctor requests them."),
                    new Upcoming("Sample bench", 7, "Scan a sample code to collect, receive or reject it."),
                    new Upcoming("Result entry", 8, "Enter results with the reference range beside each value."))));
        };
        return new DashboardResponse(user.role(), widgets);
    }

    // ------------------------------------------------------------------ roles

    private List<Widget> doctor(AuthUser user) {
        UUID doctorId = doctors.findByUserId(user.id()).map(Doctor::getId)
                .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "No doctor profile for this account"));
        Window today = today();
        LocalDate date = LocalDate.now(zone);
        return List.of(
                Widget.stats(List.of(
                        new Stat("appointmentsToday", "Appointments today", queries.appointmentsBetween(today, doctorId), null),
                        new Stat("seenToday", "Seen today", queries.appointmentsWithStatus(today, doctorId, "COMPLETED"), null),
                        new Stat("waiting", "Checked in, waiting", queries.appointmentsWithStatus(today, doctorId, "CHECKED_IN"), null),
                        new Stat("underCare", "Patients under your care", queries.patientsUnderCare(doctorId), null))),
                new Widget("schedule", "Today's schedule", "wide",
                        queries.schedule(today, doctorId).stream().map(e -> scheduleRow(e, date)).toList()),
                new Widget("recentRecords", "Records you opened", "narrow", queries.recentAccess(user.id(), 6)));
    }

    private List<Widget> reception() {
        Window today = today();
        LocalDate date = LocalDate.now(zone);
        return List.of(
                Widget.stats(List.of(
                        new Stat("registeredToday", "Registered today", queries.patientsRegisteredBetween(today), null),
                        new Stat("totalPatients", "Patients on record", queries.totalPatients(), null),
                        new Stat("pendingCodes", "Waiting to link their login", queries.pendingRegistrationCodes(Instant.now()),
                                "Registration codes not used yet"),
                        new Stat("appointmentsToday", "Appointments today", queries.appointmentsBetween(today, null), null))),
                new Widget("schedule", "Today's appointments", "wide",
                        queries.schedule(today, null).stream().map(e -> scheduleRow(e, date)).toList()),
                new Widget("recentPatients", "Recently registered", "narrow",
                        queries.recentPatients(8).stream().map(p -> Map.of(
                                "id", p.id(), "patientCode", p.patientCode(), "fullName", p.fullName(),
                                "age", Period.between(p.dob(), date).getYears(), "gender", p.gender(),
                                "hasLogin", p.hasLogin(), "registeredAt", p.registeredAt())).toList()));
    }

    private List<Widget> admin() {
        Window today = today();
        Map<String, Long> byRole = queries.activeUsersByRole();
        long staff = byRole.entrySet().stream().filter(e -> !"PATIENT".equals(e.getKey())).mapToLong(Map.Entry::getValue).sum();
        return List.of(
                Widget.stats(List.of(
                        new Stat("totalPatients", "Patients on record", queries.totalPatients(), null),
                        new Stat("registeredToday", "Registered today", queries.patientsRegisteredBetween(today), null),
                        new Stat("recordOpensToday", "Record opens today", queries.recordOpensBetween(today, null), null),
                        new Stat("activeStaff", "Active staff accounts", staff, null))),
                new Widget("accessLog", "Record access log", "wide", queries.recentAccess(null, 8)),
                new Widget("team", "Accounts by role", "narrow", byRole));
    }

    private List<Widget> patient(AuthUser user) {
        Patient patient = patients.findByUserId(user.id())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "No patient record for this account"));
        return List.of(
                new Widget("myRecord", "Your record", "narrow", Map.of(
                        "patientCode", patient.getPatientCode(),
                        "bloodGroup", nullable(patient.getBloodGroup()),
                        "knownAllergies", nullable(patient.getKnownAllergies()),
                        "hasHistory", patient.getMedicalHistory() != null,
                        "updatedAt", patient.getUpdatedAt())),
                new Widget("upcomingAppointments", "Upcoming appointments", "wide",
                        queries.upcomingForPatient(patient.getId(), Instant.now(), 5)));
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
