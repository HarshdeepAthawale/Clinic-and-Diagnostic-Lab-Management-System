package com.cdlms.appointment;

import com.cdlms.appointment.AppointmentDtos.AppointmentView;
import com.cdlms.appointment.AppointmentDtos.DoctorOption;
import com.cdlms.appointment.AppointmentDtos.DoctorRef;
import com.cdlms.appointment.AppointmentDtos.EventView;
import com.cdlms.appointment.AppointmentDtos.PatientRef;
import com.cdlms.common.ClinicTime;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Read side of appointments: joined views for lists, the queue board and the slot picker. Writes go
 * through the {@link Appointment} entity in {@link AppointmentService}.
 */
@Repository
public class AppointmentQueries {

    private static final String SELECT_VIEW = """
            SELECT a.id, a.kind, a.status, a.scheduled_at, a.duration_minutes, a.reason, a.queue_token,
                   a.checked_in_at, a.started_at, a.completed_at, a.cancelled_at, a.cancellation_reason, a.created_at,
                   p.id AS patient_id, p.patient_code, p.full_name AS patient_name, p.dob, p.gender,
                   d.id AS doctor_id, d.full_name AS doctor_name, d.specialization
            FROM appointments a
            JOIN patients p ON p.id = a.patient_id
            JOIN doctors d ON d.id = a.doctor_id
            """;

    private final NamedParameterJdbcTemplate jdbc;
    private final ClinicTime time;

    public AppointmentQueries(NamedParameterJdbcTemplate jdbc, ClinicTime time) {
        this.jdbc = jdbc;
        this.time = time;
    }

    /**
     * Appointments in {@code [from, to)}, oldest first. {@code doctorId}/{@code patientId} narrow the list
     * when set; cancelled ones are left out unless asked for.
     */
    public List<AppointmentView> list(Instant from, Instant to, UUID doctorId, UUID patientId, boolean withCancelled) {
        return jdbc.query(SELECT_VIEW + """
                WHERE a.scheduled_at >= :from AND a.scheduled_at < :to
                  AND (CAST(:doctorId AS uuid) IS NULL OR a.doctor_id = CAST(:doctorId AS uuid))
                  AND (CAST(:patientId AS uuid) IS NULL OR a.patient_id = CAST(:patientId AS uuid))
                  AND (:withCancelled OR a.status <> 'CANCELLED')
                ORDER BY a.scheduled_at, a.queue_number NULLS LAST
                """, new MapSqlParameterSource("from", Timestamp.from(from)).addValue("to", Timestamp.from(to))
                .addValue("doctorId", doctorId).addValue("patientId", patientId)
                .addValue("withCancelled", withCancelled), viewMapper());
    }

    /**
     * A patient's appointments. Upcoming = still live and not before {@code since} (start of today),
     * soonest first; past = everything else, latest first.
     */
    public List<AppointmentView> forPatient(UUID patientId, Instant since, boolean upcoming, int limit) {
        String live = "a.status IN ('BOOKED', 'CHECKED_IN', 'IN_CONSULTATION') AND a.scheduled_at >= :since";
        String where = upcoming
                ? "WHERE a.patient_id = :patientId AND " + live + " ORDER BY a.scheduled_at"
                : "WHERE a.patient_id = :patientId AND NOT (" + live + ") ORDER BY a.scheduled_at DESC";
        return jdbc.query(SELECT_VIEW + where + " LIMIT :limit",
                new MapSqlParameterSource("patientId", patientId).addValue("since", Timestamp.from(since))
                        .addValue("limit", limit), viewMapper());
    }

    public Optional<AppointmentView> find(UUID id) {
        return jdbc.query(SELECT_VIEW + "WHERE a.id = :id", new MapSqlParameterSource("id", id), viewMapper())
                .stream().findFirst();
    }

    public List<EventView> history(UUID appointmentId) {
        return jdbc.query("""
                SELECT e.from_status, e.to_status, e.note, e.created_at,
                       COALESCE(d.full_name, s.full_name, p.full_name, u.email) AS changed_by
                FROM appointment_events e
                LEFT JOIN users u ON u.id = e.changed_by_user_id
                LEFT JOIN doctors d ON d.user_id = u.id
                LEFT JOIN staff s ON s.user_id = u.id
                LEFT JOIN patients p ON p.user_id = u.id
                WHERE e.appointment_id = :id
                ORDER BY e.created_at
                """, new MapSqlParameterSource("id", appointmentId), (rs, i) -> new EventView(
                status(rs.getString("from_status")), status(rs.getString("to_status")),
                rs.getString("changed_by"), rs.getString("note"), rs.getTimestamp("created_at").toInstant()));
    }

    /** Start times already taken by live bookings of this doctor within {@code [from, to)}. */
    public Set<Instant> takenSlots(UUID doctorId, Instant from, Instant to) {
        return new HashSet<>(jdbc.query("""
                SELECT scheduled_at FROM appointments
                WHERE doctor_id = :doctorId AND kind = 'SCHEDULED' AND status <> 'CANCELLED'
                  AND scheduled_at >= :from AND scheduled_at < :to
                """, new MapSqlParameterSource("doctorId", doctorId).addValue("from", Timestamp.from(from))
                .addValue("to", Timestamp.from(to)), (rs, i) -> rs.getTimestamp("scheduled_at").toInstant()));
    }

    /** Everyone who got a token on {@code date}, in token order; for one doctor or all. */
    public List<AppointmentView> queue(LocalDate date, UUID doctorId) {
        return jdbc.query(SELECT_VIEW + """
                WHERE a.queue_date = :date
                  AND (CAST(:doctorId AS uuid) IS NULL OR a.doctor_id = CAST(:doctorId AS uuid))
                ORDER BY a.queue_number
                """, new MapSqlParameterSource("date", date).addValue("doctorId", doctorId), viewMapper());
    }

    public List<DoctorOption> doctors() {
        return jdbc.query("""
                SELECT d.id, d.full_name, d.specialization,
                       EXISTS (SELECT 1 FROM doctor_schedules s WHERE s.doctor_id = d.id) AS has_hours
                FROM doctors d JOIN users u ON u.id = d.user_id
                WHERE u.is_active
                ORDER BY d.full_name
                """, new MapSqlParameterSource(), (rs, i) -> new DoctorOption(rs.getObject("id", UUID.class),
                rs.getString("full_name"), rs.getString("specialization"), rs.getBoolean("has_hours")));
    }

    public Optional<DoctorRef> doctor(UUID doctorId) {
        return jdbc.query("SELECT id, full_name, specialization FROM doctors WHERE id = :id",
                new MapSqlParameterSource("id", doctorId), (rs, i) -> new DoctorRef(rs.getObject("id", UUID.class),
                        rs.getString("full_name"), rs.getString("specialization"))).stream().findFirst();
    }

    /** Counts per status for one clinic day, e.g. {BOOKED: 4, COMPLETED: 9}. */
    public Map<String, Long> statusCounts(Instant from, Instant to) {
        return jdbc.query("""
                SELECT status, count(*) AS n FROM appointments
                WHERE scheduled_at >= :from AND scheduled_at < :to GROUP BY status
                """, new MapSqlParameterSource("from", Timestamp.from(from)).addValue("to", Timestamp.from(to)),
                (rs, i) -> Map.entry(rs.getString("status"), rs.getLong("n")))
                .stream().collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private RowMapper<AppointmentView> viewMapper() {
        LocalDate today = time.today();
        return (rs, i) -> new AppointmentView(
                rs.getObject("id", UUID.class),
                Appointment.Kind.valueOf(rs.getString("kind")),
                AppointmentStatus.valueOf(rs.getString("status")),
                instant(rs, "scheduled_at"),
                rs.getInt("duration_minutes"),
                rs.getString("reason"),
                rs.getString("queue_token"),
                new PatientRef(rs.getObject("patient_id", UUID.class), rs.getString("patient_code"),
                        rs.getString("patient_name"),
                        Period.between(rs.getObject("dob", LocalDate.class), today).getYears(),
                        rs.getString("gender")),
                new DoctorRef(rs.getObject("doctor_id", UUID.class), rs.getString("doctor_name"),
                        rs.getString("specialization")),
                instant(rs, "checked_in_at"),
                instant(rs, "started_at"),
                instant(rs, "completed_at"),
                instant(rs, "cancelled_at"),
                rs.getString("cancellation_reason"),
                instant(rs, "created_at"));
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp ts = rs.getTimestamp(column);
        return ts == null ? null : ts.toInstant();
    }

    private static AppointmentStatus status(String value) {
        return value == null ? null : AppointmentStatus.valueOf(value);
    }
}
