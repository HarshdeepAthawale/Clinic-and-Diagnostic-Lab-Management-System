package com.cdlms.consultation;

import com.cdlms.appointment.AppointmentDtos.PatientRef;
import com.cdlms.common.ClinicTime;
import com.cdlms.consultation.ConsultationDtos.ConsultationSummary;
import com.cdlms.consultation.ConsultationDtos.DoctorCard;
import com.cdlms.consultation.ConsultationDtos.ItemView;
import com.cdlms.consultation.ConsultationDtos.MedicineSuggestion;
import com.cdlms.consultation.ConsultationDtos.PrescriptionView;
import com.cdlms.consultation.ConsultationDtos.Ref;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.Period;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/** Read side of consultations and prescriptions: joined views, histories, medicine suggestions. */
@Repository
public class ConsultationQueries {

    private static final String SELECT_PRESCRIPTION = """
            SELECT rx.id, rx.code, rx.consultation_id, c.appointment_id, rx.issued_at, rx.advice, rx.revision_reason,
                   c.diagnosis,
                   old.id AS replaces_id, old.code AS replaces_code,
                   newer.id AS superseded_id, newer.code AS superseded_code,
                   p.id AS patient_id, p.patient_code, p.full_name AS patient_name, p.dob, p.gender,
                   d.id AS doctor_id, d.full_name AS doctor_name, d.specialization, d.qualification, d.registration_number
            FROM prescriptions rx
            JOIN consultations c ON c.id = rx.consultation_id
            JOIN patients p ON p.id = rx.patient_id
            JOIN doctors d ON d.id = rx.doctor_id
            LEFT JOIN prescriptions old ON old.id = rx.replaces_id
            LEFT JOIN prescriptions newer ON newer.replaces_id = rx.id
            """;

    private final NamedParameterJdbcTemplate jdbc;
    private final ClinicTime time;

    public ConsultationQueries(NamedParameterJdbcTemplate jdbc, ClinicTime time) {
        this.jdbc = jdbc;
        this.time = time;
    }

    // ------------------------------------------------------------------ prescriptions

    public Optional<PrescriptionView> prescription(UUID id) {
        return prescriptions("WHERE rx.id = :id", new MapSqlParameterSource("id", id)).stream().findFirst();
    }

    public List<PrescriptionView> prescriptionsForConsultation(UUID consultationId) {
        return prescriptions("WHERE rx.consultation_id = :id ORDER BY rx.issued_at",
                new MapSqlParameterSource("id", consultationId));
    }

    /** A patient's prescriptions, newest first; replaced ones included (marked by {@code supersededBy}). */
    public List<PrescriptionView> prescriptionsForPatient(UUID patientId, int limit) {
        return prescriptions("WHERE rx.patient_id = :id ORDER BY rx.issued_at DESC LIMIT :limit",
                new MapSqlParameterSource("id", patientId).addValue("limit", limit));
    }

    private List<PrescriptionView> prescriptions(String where, MapSqlParameterSource params) {
        LocalDate today = time.today();
        List<PrescriptionView> rows = jdbc.query(SELECT_PRESCRIPTION + where, params, (rs, i) -> new PrescriptionView(
                rs.getObject("id", UUID.class), rs.getString("code"), rs.getObject("consultation_id", UUID.class),
                rs.getObject("appointment_id", UUID.class), rs.getTimestamp("issued_at").toInstant(),
                patient(rs, today), doctor(rs), rs.getString("diagnosis"), rs.getString("advice"), List.of(),
                ref(rs, "replaces_id", "replaces_code"), rs.getString("revision_reason"),
                ref(rs, "superseded_id", "superseded_code")));
        if (rows.isEmpty()) {
            return rows;
        }
        Map<UUID, List<ItemView>> items = jdbc.query("""
                SELECT prescription_id, position, medicine, dose, frequency, duration_days, instructions
                FROM prescription_items WHERE prescription_id IN (:ids) ORDER BY position
                """, new MapSqlParameterSource("ids", rows.stream().map(PrescriptionView::id).toList()),
                (rs, i) -> Map.entry(rs.getObject("prescription_id", UUID.class), new ItemView(rs.getInt("position"),
                        rs.getString("medicine"), rs.getString("dose"), rs.getString("frequency"),
                        (Integer) rs.getObject("duration_days", Integer.class), rs.getString("instructions"))))
                .stream().collect(Collectors.groupingBy(Map.Entry::getKey,
                        Collectors.mapping(Map.Entry::getValue, Collectors.toList())));
        return rows.stream().map(r -> new PrescriptionView(r.id(), r.code(), r.consultationId(), r.appointmentId(),
                r.issuedAt(), r.patient(), r.doctor(), r.diagnosis(), r.advice(), items.getOrDefault(r.id(), List.of()),
                r.replaces(), r.revisionReason(), r.supersededBy())).toList();
    }

    // ------------------------------------------------------------------ consultation lists

    private static final String SELECT_SUMMARY = """
            SELECT c.id, c.appointment_id, c.created_at, c.completed_at, c.chief_complaint, c.diagnosis,
                   p.id AS patient_id, p.patient_code, p.full_name AS patient_name, p.dob, p.gender,
                   d.id AS doctor_id, d.full_name AS doctor_name, d.specialization, d.qualification, d.registration_number,
                   rx.id AS rx_id, rx.code AS rx_code
            FROM consultations c
            JOIN patients p ON p.id = c.patient_id
            JOIN doctors d ON d.id = c.doctor_id
            -- the current prescription: the one nothing has replaced
            LEFT JOIN LATERAL (
                SELECT x.id, x.code FROM prescriptions x
                WHERE x.consultation_id = c.id
                  AND NOT EXISTS (SELECT 1 FROM prescriptions y WHERE y.replaces_id = x.id)
                ORDER BY x.issued_at DESC LIMIT 1
            ) rx ON true
            """;

    public List<ConsultationSummary> forPatient(UUID patientId, int limit) {
        return summaries("WHERE c.patient_id = :id ORDER BY c.created_at DESC LIMIT :limit",
                new MapSqlParameterSource("id", patientId).addValue("limit", limit));
    }

    public List<ConsultationSummary> forDoctor(UUID doctorId, int offset, int limit) {
        return summaries("WHERE c.doctor_id = :id ORDER BY c.created_at DESC OFFSET :offset LIMIT :limit",
                new MapSqlParameterSource("id", doctorId).addValue("offset", offset).addValue("limit", limit));
    }

    public long countForDoctor(UUID doctorId) {
        Long n = jdbc.queryForObject("SELECT count(*) FROM consultations WHERE doctor_id = :id",
                new MapSqlParameterSource("id", doctorId), Long.class);
        return n == null ? 0 : n;
    }

    private List<ConsultationSummary> summaries(String where, MapSqlParameterSource params) {
        LocalDate today = time.today();
        return jdbc.query(SELECT_SUMMARY + where, params, (rs, i) -> new ConsultationSummary(
                rs.getObject("id", UUID.class), rs.getObject("appointment_id", UUID.class),
                rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("completed_at") != null,
                patient(rs, today), doctor(rs), rs.getString("chief_complaint"), rs.getString("diagnosis"),
                ref(rs, "rx_id", "rx_code")));
    }

    public PatientRef patientRef(UUID patientId) {
        LocalDate today = time.today();
        return jdbc.queryForObject("""
                SELECT id AS patient_id, patient_code, full_name AS patient_name, dob, gender FROM patients WHERE id = :id
                """, new MapSqlParameterSource("id", patientId), (rs, i) -> patient(rs, today));
    }

    public DoctorCard doctorCard(UUID doctorId) {
        return jdbc.queryForObject("""
                SELECT id AS doctor_id, full_name AS doctor_name, specialization, qualification, registration_number
                FROM doctors WHERE id = :id
                """, new MapSqlParameterSource("id", doctorId), (rs, i) -> doctor(rs));
    }

    // ------------------------------------------------------------------ medicines

    /**
     * Medicines this clinic has prescribed before, most used first, with the most common dose,
     * frequency and duration for each — built from real prescriptions, no seeded drug list.
     */
    public List<MedicineSuggestion> medicines(String query, int limit) {
        return jdbc.query("""
                SELECT medicine, count(*) AS n,
                       mode() WITHIN GROUP (ORDER BY dose) AS dose,
                       mode() WITHIN GROUP (ORDER BY frequency) AS frequency,
                       mode() WITHIN GROUP (ORDER BY duration_days) AS duration_days
                FROM (SELECT DISTINCT ON (lower(medicine), prescription_id) * FROM prescription_items) i
                WHERE lower(medicine) LIKE :pattern
                GROUP BY medicine
                ORDER BY n DESC, medicine
                LIMIT :limit
                """, new MapSqlParameterSource("pattern", "%" + query.toLowerCase() + "%").addValue("limit", limit),
                (rs, i) -> new MedicineSuggestion(rs.getString("medicine"), rs.getString("dose"),
                        rs.getString("frequency"), (Integer) rs.getObject("duration_days", Integer.class),
                        rs.getLong("n")));
    }

    // ------------------------------------------------------------------ mapping

    private static PatientRef patient(ResultSet rs, LocalDate today) throws SQLException {
        return new PatientRef(rs.getObject("patient_id", UUID.class), rs.getString("patient_code"),
                rs.getString("patient_name"), Period.between(rs.getObject("dob", LocalDate.class), today).getYears(),
                rs.getString("gender"));
    }

    private static DoctorCard doctor(ResultSet rs) throws SQLException {
        return new DoctorCard(rs.getObject("doctor_id", UUID.class), rs.getString("doctor_name"),
                rs.getString("specialization"), rs.getString("qualification"), rs.getString("registration_number"));
    }

    private static Ref ref(ResultSet rs, String idColumn, String codeColumn) throws SQLException {
        UUID id = rs.getObject(idColumn, UUID.class);
        return id == null ? null : new Ref(id, rs.getString(codeColumn));
    }
}
