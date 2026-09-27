package com.cdlms.consultation;

import com.cdlms.consultation.ConsultationDtos.ConsultationSummary;
import com.cdlms.consultation.ConsultationDtos.FormularyItem;
import com.cdlms.consultation.ConsultationDtos.PrescriptionSummary;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;

/** Read-only list queries (one SQL each) behind visit histories, the doctor's list and the formulary. */
@Repository
public class ConsultationQueries {

    private static final String SUMMARY_SELECT = """
            SELECT c.id, c.status, COALESCE(a.started_at, c.created_at) AS visit_at,
                   p.id AS patient_id, p.patient_code, p.full_name AS patient_name,
                   d.full_name AS doctor_name, c.diagnosis,
                   rx.id AS prescription_id, rx.prescription_code,
                   (SELECT count(*) FROM prescription_items i WHERE i.prescription_id = rx.id) AS medicine_count
            FROM consultations c
            JOIN appointments a ON a.id = c.appointment_id
            JOIN patients p ON p.id = c.patient_id
            JOIN doctors d ON d.id = c.doctor_id
            LEFT JOIN prescriptions rx ON rx.consultation_id = c.id AND rx.issued_at IS NOT NULL
            """;

    private static final RowMapper<ConsultationSummary> SUMMARY = (rs, i) -> new ConsultationSummary(
            rs.getObject("id", UUID.class), Consultation.Status.valueOf(rs.getString("status")),
            rs.getTimestamp("visit_at").toInstant(), rs.getObject("patient_id", UUID.class),
            rs.getString("patient_code"), rs.getString("patient_name"), rs.getString("doctor_name"),
            rs.getString("diagnosis"), rs.getObject("prescription_id", UUID.class), rs.getString("prescription_code"),
            rs.getInt("medicine_count"));

    private final NamedParameterJdbcTemplate jdbc;

    public ConsultationQueries(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** A patient's completed visits, newest first. */
    public List<ConsultationSummary> completedForPatient(UUID patientId, int limit) {
        return jdbc.query(SUMMARY_SELECT + """
                WHERE c.patient_id = :patientId AND c.status = 'COMPLETED'
                ORDER BY visit_at DESC LIMIT :limit
                """, new MapSqlParameterSource("patientId", patientId).addValue("limit", limit), SUMMARY);
    }

    /** A doctor's own consultations (open drafts first, then newest), optionally only since a time. */
    public List<ConsultationSummary> forDoctor(UUID doctorId, Timestamp since, int limit, int offset) {
        return jdbc.query(SUMMARY_SELECT + """
                WHERE c.doctor_id = :doctorId
                  AND (CAST(:since AS timestamptz) IS NULL OR c.created_at >= CAST(:since AS timestamptz))
                ORDER BY (c.status = 'DRAFT') DESC, visit_at DESC LIMIT :limit OFFSET :offset
                """, new MapSqlParameterSource("doctorId", doctorId).addValue("since", since)
                .addValue("limit", limit).addValue("offset", offset), SUMMARY);
    }

    public long countForDoctor(UUID doctorId, Timestamp since) {
        Long n = jdbc.queryForObject("""
                SELECT count(*) FROM consultations c
                WHERE c.doctor_id = :doctorId
                  AND (CAST(:since AS timestamptz) IS NULL OR c.created_at >= CAST(:since AS timestamptz))
                """, new MapSqlParameterSource("doctorId", doctorId).addValue("since", since), Long.class);
        return n == null ? 0 : n;
    }

    /** A patient's issued prescriptions, newest first. */
    public List<PrescriptionSummary> prescriptionsForPatient(UUID patientId, int limit) {
        return jdbc.query("""
                SELECT rx.id, rx.prescription_code, rx.issued_at, c.id AS consultation_id,
                       d.full_name AS doctor_name, d.specialization, c.diagnosis,
                       (SELECT count(*) FROM prescription_items i WHERE i.prescription_id = rx.id) AS medicine_count
                FROM prescriptions rx
                JOIN consultations c ON c.id = rx.consultation_id
                JOIN doctors d ON d.id = c.doctor_id
                WHERE c.patient_id = :patientId AND rx.issued_at IS NOT NULL
                ORDER BY rx.issued_at DESC LIMIT :limit
                """, new MapSqlParameterSource("patientId", patientId).addValue("limit", limit),
                (rs, i) -> new PrescriptionSummary(rs.getObject("id", UUID.class), rs.getString("prescription_code"),
                        rs.getTimestamp("issued_at").toInstant(), rs.getObject("consultation_id", UUID.class),
                        rs.getString("doctor_name"), rs.getString("specialization"), rs.getString("diagnosis"),
                        rs.getInt("medicine_count")));
    }

    /** Formulary entries whose name starts with, then contains, the query. */
    public List<FormularyItem> formulary(String query, int limit) {
        String q = query.trim().toLowerCase();
        return jdbc.query("""
                SELECT name, form, default_strength FROM formulary
                WHERE lower(name) LIKE :contains
                ORDER BY (lower(name) LIKE :prefix) DESC, name, form
                LIMIT :limit
                """, new MapSqlParameterSource("contains", "%" + q + "%").addValue("prefix", q + "%")
                .addValue("limit", limit),
                (rs, i) -> new FormularyItem(rs.getString("name"), rs.getString("form"), rs.getString("default_strength")));
    }
}
