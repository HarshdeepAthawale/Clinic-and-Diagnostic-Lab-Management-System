package com.cdlms.dashboard;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Read-only aggregate queries behind the dashboards. Kept as plain SQL (not entities) so each
 * widget is one cheap, indexed query; later phases add their own methods here.
 */
@Repository
public class DashboardQueries {

    private final NamedParameterJdbcTemplate jdbc;

    public DashboardQueries(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record Window(Instant from, Instant to) {
        MapSqlParameterSource params() {
            return new MapSqlParameterSource("from", Timestamp.from(from)).addValue("to", Timestamp.from(to));
        }
    }

    // ------------------------------------------------------------------ patients

    public long totalPatients() {
        return count("SELECT count(*) FROM patients", new MapSqlParameterSource());
    }

    public long patientsRegisteredBetween(Window window) {
        return count("SELECT count(*) FROM patients WHERE created_at >= :from AND created_at < :to", window.params());
    }

    /** Front-desk registrations whose registration code is still waiting to be used. */
    public long pendingRegistrationCodes(Instant now) {
        return count("SELECT count(*) FROM patients WHERE user_id IS NULL AND claim_code_expires_at > :now",
                new MapSqlParameterSource("now", Timestamp.from(now)));
    }

    public record RecentPatient(UUID id, String patientCode, String fullName, LocalDate dob, String gender,
                                boolean hasLogin, Instant registeredAt) {
    }

    public List<RecentPatient> recentPatients(int limit) {
        return jdbc.query("""
                SELECT id, patient_code, full_name, dob, gender, user_id IS NOT NULL AS has_login, created_at
                FROM patients ORDER BY created_at DESC LIMIT :limit
                """, new MapSqlParameterSource("limit", limit), (rs, i) -> new RecentPatient(
                rs.getObject("id", UUID.class), rs.getString("patient_code"), rs.getString("full_name"),
                rs.getObject("dob", LocalDate.class), rs.getString("gender"), rs.getBoolean("has_login"),
                rs.getTimestamp("created_at").toInstant()));
    }

    // ------------------------------------------------------------------ appointments

    public long appointmentsBetween(Window window, UUID doctorId) {
        MapSqlParameterSource params = window.params().addValue("doctorId", doctorId);
        return count("""
                SELECT count(*) FROM appointments
                WHERE scheduled_at >= :from AND scheduled_at < :to AND status <> 'CANCELLED'
                  AND (CAST(:doctorId AS uuid) IS NULL OR doctor_id = CAST(:doctorId AS uuid))
                """, params);
    }

    public long appointmentsWithStatus(Window window, UUID doctorId, String status) {
        MapSqlParameterSource params = window.params().addValue("doctorId", doctorId).addValue("status", status);
        return count("""
                SELECT count(*) FROM appointments
                WHERE scheduled_at >= :from AND scheduled_at < :to AND status = :status
                  AND (CAST(:doctorId AS uuid) IS NULL OR doctor_id = CAST(:doctorId AS uuid))
                """, params);
    }

    public record ScheduleEntry(UUID appointmentId, Instant scheduledAt, String status, String queueToken,
                                UUID patientId, String patientCode, String patientName, LocalDate dob, String gender,
                                String doctorName) {
    }

    /** Appointments in the window, oldest first; for one doctor, or all when {@code doctorId} is null. */
    public List<ScheduleEntry> schedule(Window window, UUID doctorId) {
        MapSqlParameterSource params = window.params().addValue("doctorId", doctorId);
        return jdbc.query("""
                SELECT a.id, a.scheduled_at, a.status, a.queue_token,
                       p.id AS patient_id, p.patient_code, p.full_name, p.dob, p.gender, d.full_name AS doctor_name
                FROM appointments a
                JOIN patients p ON p.id = a.patient_id
                JOIN doctors d ON d.id = a.doctor_id
                WHERE a.scheduled_at >= :from AND a.scheduled_at < :to AND a.status <> 'CANCELLED'
                  AND (CAST(:doctorId AS uuid) IS NULL OR a.doctor_id = CAST(:doctorId AS uuid))
                ORDER BY a.scheduled_at
                """, params, (rs, i) -> new ScheduleEntry(rs.getObject("id", UUID.class),
                rs.getTimestamp("scheduled_at").toInstant(), rs.getString("status"), rs.getString("queue_token"),
                rs.getObject("patient_id", UUID.class), rs.getString("patient_code"), rs.getString("full_name"),
                rs.getObject("dob", LocalDate.class), rs.getString("gender"), rs.getString("doctor_name")));
    }

    /** Distinct patients the doctor has a care relationship with (ADR-015). */
    public long patientsUnderCare(UUID doctorId) {
        return count("SELECT count(DISTINCT patient_id) FROM appointments WHERE doctor_id = :doctorId AND status <> 'CANCELLED'",
                new MapSqlParameterSource("doctorId", doctorId));
    }

    public record UpcomingAppointment(UUID appointmentId, Instant scheduledAt, String status, String doctorName,
                                      String specialization) {
    }

    public List<UpcomingAppointment> upcomingForPatient(UUID patientId, Instant now, int limit) {
        return jdbc.query("""
                SELECT a.id, a.scheduled_at, a.status, d.full_name, d.specialization
                FROM appointments a JOIN doctors d ON d.id = a.doctor_id
                WHERE a.patient_id = :patientId AND a.scheduled_at >= :now
                  AND a.status IN ('BOOKED', 'CHECKED_IN')
                ORDER BY a.scheduled_at LIMIT :limit
                """, new MapSqlParameterSource("patientId", patientId).addValue("now", Timestamp.from(now))
                .addValue("limit", limit), (rs, i) -> new UpcomingAppointment(rs.getObject("id", UUID.class),
                rs.getTimestamp("scheduled_at").toInstant(), rs.getString("status"), rs.getString("full_name"),
                rs.getString("specialization")));
    }

    // ------------------------------------------------------------------ access log & staff

    public long recordOpensBetween(Window window, UUID userId) {
        MapSqlParameterSource params = window.params().addValue("userId", userId);
        return count("""
                SELECT count(*) FROM patient_access_log
                WHERE accessed_at >= :from AND accessed_at < :to
                  AND (CAST(:userId AS uuid) IS NULL OR user_id = CAST(:userId AS uuid))
                """, params);
    }

    public record RecentAccess(Instant accessedAt, String resource, UUID patientId, String patientCode,
                               String patientName, String userName, String userRole) {
    }

    /** Latest record opens; for one user, or everyone when {@code userId} is null. */
    public List<RecentAccess> recentAccess(UUID userId, int limit) {
        return jdbc.query("""
                SELECT l.accessed_at, l.resource, p.id AS patient_id, p.patient_code, p.full_name AS patient_name,
                       COALESCE(d.full_name, pa.full_name, s.full_name, u.email) AS user_name, u.role
                FROM patient_access_log l
                JOIN patients p ON p.id = l.patient_id
                JOIN users u ON u.id = l.user_id
                LEFT JOIN doctors d ON d.user_id = u.id
                LEFT JOIN pathologists pa ON pa.user_id = u.id
                LEFT JOIN staff s ON s.user_id = u.id
                WHERE CAST(:userId AS uuid) IS NULL OR l.user_id = CAST(:userId AS uuid)
                ORDER BY l.accessed_at DESC LIMIT :limit
                """, new MapSqlParameterSource("userId", userId).addValue("limit", limit),
                (rs, i) -> new RecentAccess(rs.getTimestamp("accessed_at").toInstant(), rs.getString("resource"),
                        rs.getObject("patient_id", UUID.class), rs.getString("patient_code"),
                        rs.getString("patient_name"), rs.getString("user_name"), rs.getString("role")));
    }

    /** Active accounts per role, e.g. {DOCTOR: 4, RECEPTIONIST: 2, …}. */
    public Map<String, Long> activeUsersByRole() {
        return jdbc.query("SELECT role, count(*) AS n FROM users WHERE is_active GROUP BY role ORDER BY role",
                new MapSqlParameterSource(), rs -> {
                    Map<String, Long> result = new LinkedHashMap<>();
                    while (rs.next()) {
                        result.put(rs.getString("role"), rs.getLong("n"));
                    }
                    return result;
                });
    }

    // ------------------------------------------------------------------ consultations

    public record OpenConsultation(UUID consultationId, UUID patientId, String patientName, String patientCode,
                                   String queueToken, Instant startedAt) {
    }

    /** The doctor's consultation still in progress (a draft), if any. */
    public Optional<OpenConsultation> openConsultation(UUID doctorId) {
        return jdbc.query("""
                SELECT c.id, p.id AS patient_id, p.full_name, p.patient_code, a.queue_token,
                       COALESCE(a.started_at, c.created_at) AS started_at
                FROM consultations c
                JOIN appointments a ON a.id = c.appointment_id
                JOIN patients p ON p.id = c.patient_id
                WHERE c.doctor_id = :doctorId AND c.status = 'DRAFT'
                ORDER BY c.created_at DESC LIMIT 1
                """, new MapSqlParameterSource("doctorId", doctorId), (rs, i) -> new OpenConsultation(
                rs.getObject("id", UUID.class), rs.getObject("patient_id", UUID.class), rs.getString("full_name"),
                rs.getString("patient_code"), rs.getString("queue_token"), rs.getTimestamp("started_at").toInstant()))
                .stream().findFirst();
    }

    private long count(String sql, MapSqlParameterSource params) {
        Long value = jdbc.queryForObject(sql, params, Long.class);
        return value == null ? 0 : value;
    }
}
