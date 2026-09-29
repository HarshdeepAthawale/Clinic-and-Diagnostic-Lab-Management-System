package com.cdlms.result;

import com.cdlms.lab.LabOrder;
import com.cdlms.lab.TubeType;
import com.cdlms.result.ResultDtos.ReportRow;
import com.cdlms.result.ResultDtos.TestingRow;
import com.cdlms.result.ResultDtos.TrendPoint;
import com.cdlms.result.ResultDtos.VerificationRow;
import com.cdlms.sample.SampleStatus;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Array;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Read-only list queries (one SQL each) behind the bench, the verification queue, report lists and trends. */
@Repository
public class ResultQueries {

    private static final String TEST_NAMES = """
            COALESCE((SELECT array_agg(i.test_name ORDER BY i.created_at)
                      FROM sample_items si JOIN lab_order_items i ON i.id = si.lab_order_item_id
                      WHERE si.sample_id = s.id), '{}')""";

    private final NamedParameterJdbcTemplate jdbc;

    public ResultQueries(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ---------------------------------------------------------------- the bench

    /**
     * Samples at the lab waiting to be tested (accepted, or back from the pathologist), returned ones first
     * with the reason, then urgent orders, then the longest waiting.
     */
    public List<TestingRow> toTest(int limit, int offset) {
        return jdbc.query("""
                SELECT s.id, s.sample_code, s.status, s.required_tube_type, o.id AS order_id, o.order_code, o.priority,
                       p.id AS patient_id, p.full_name AS patient_name, s.received_at,
                       %s AS test_names,
                       (SELECT count(*) FROM sample_results r WHERE r.sample_id = s.id AND r.status = 'RETURNED_FOR_RETEST') AS retests,
                       last.return_reason, last.return_note
                FROM samples s
                JOIN lab_orders o ON o.id = s.lab_order_id
                JOIN patients p ON p.id = s.patient_id
                LEFT JOIN LATERAL (SELECT r.return_reason, r.return_note FROM sample_results r
                                   WHERE r.sample_id = s.id AND r.status = 'RETURNED_FOR_RETEST'
                                   ORDER BY r.attempt_number DESC LIMIT 1) last ON s.status = 'IN_TESTING'
                WHERE s.status IN ('RECEIVED_AT_LAB', 'IN_TESTING')
                ORDER BY (last.return_reason IS NOT NULL) DESC, (o.priority = 'URGENT') DESC, s.received_at
                LIMIT :limit OFFSET :offset
                """.formatted(TEST_NAMES), new MapSqlParameterSource("limit", limit).addValue("offset", offset),
                (rs, i) -> new TestingRow(rs.getObject("id", UUID.class), rs.getString("sample_code"),
                        SampleStatus.valueOf(rs.getString("status")), TubeType.valueOf(rs.getString("required_tube_type")),
                        rs.getObject("order_id", UUID.class), rs.getString("order_code"),
                        LabOrder.Priority.valueOf(rs.getString("priority")), rs.getObject("patient_id", UUID.class),
                        rs.getString("patient_name"), strings(rs.getArray("test_names")), instant(rs, "received_at"),
                        rs.getInt("retests"),
                        rs.getString("return_reason") == null ? null : SampleResult.ReturnReason.valueOf(rs.getString("return_reason")),
                        rs.getString("return_note")));
    }

    public long countToTest() {
        Long n = jdbc.queryForObject("SELECT count(*) FROM samples WHERE status IN ('RECEIVED_AT_LAB', 'IN_TESTING')",
                new MapSqlParameterSource(), Long.class);
        return n == null ? 0 : n;
    }

    /** Samples back from the pathologist, waiting to be retested. */
    public long countReturned() {
        Long n = jdbc.queryForObject("""
                SELECT count(*) FROM samples s WHERE s.status = 'IN_TESTING'
                  AND EXISTS (SELECT 1 FROM sample_results r WHERE r.sample_id = s.id AND r.status = 'RETURNED_FOR_RETEST')
                """, new MapSqlParameterSource(), Long.class);
        return n == null ? 0 : n;
    }

    // ---------------------------------------------------------------- the pathologist

    private static final String VERIFICATION_SELECT = """
            SELECT s.id AS sample_id, s.sample_code, o.id AS order_id, o.order_code, p.id AS patient_id,
                   p.full_name AS patient_name, %s AS test_names, r.attempt_number, r.entered_at, r.verified_at,
                   EXISTS (SELECT 1 FROM result_values v WHERE v.sample_result_id = r.id
                           AND v.flag IN ('CRITICAL_LOW', 'CRITICAL_HIGH')) AS critical,
                   (SELECT count(*) FROM result_values v WHERE v.sample_result_id = r.id
                    AND v.flag IS NOT NULL AND v.flag <> 'NORMAL') AS abnormal
            FROM sample_results r
            JOIN samples s ON s.id = r.sample_id
            JOIN lab_orders o ON o.id = s.lab_order_id
            JOIN patients p ON p.id = s.patient_id
            """.formatted(TEST_NAMES);

    private static final RowMapper<VerificationRow> VERIFICATION = (rs, i) -> new VerificationRow(
            rs.getObject("sample_id", UUID.class), rs.getString("sample_code"), rs.getObject("order_id", UUID.class),
            rs.getString("order_code"), rs.getObject("patient_id", UUID.class), rs.getString("patient_name"),
            strings(rs.getArray("test_names")), rs.getInt("attempt_number"), instant(rs, "entered_at"),
            instant(rs, "verified_at"), rs.getBoolean("critical"), rs.getInt("abnormal"));

    /** Results waiting for sign-off: critical first, then any out-of-range, then the longest waiting. */
    public List<VerificationRow> pendingVerification(int limit, int offset) {
        // Wrapped so the computed columns can be used inside ORDER BY expressions.
        return jdbc.query("SELECT * FROM (" + VERIFICATION_SELECT + """
                WHERE r.status = 'PENDING_VERIFICATION' AND s.status = 'RESULT_ENTERED') q
                ORDER BY critical DESC, (abnormal > 0) DESC, entered_at
                LIMIT :limit OFFSET :offset
                """, new MapSqlParameterSource("limit", limit).addValue("offset", offset), VERIFICATION);
    }

    public long countPendingVerification() {
        Long n = jdbc.queryForObject("SELECT count(*) FROM sample_results WHERE status = 'PENDING_VERIFICATION'",
                new MapSqlParameterSource(), Long.class);
        return n == null ? 0 : n;
    }

    public long countPendingCritical() {
        Long n = jdbc.queryForObject("""
                SELECT count(*) FROM sample_results r WHERE r.status = 'PENDING_VERIFICATION'
                  AND EXISTS (SELECT 1 FROM result_values v WHERE v.sample_result_id = r.id
                              AND v.flag IN ('CRITICAL_LOW', 'CRITICAL_HIGH'))
                """, new MapSqlParameterSource(), Long.class);
        return n == null ? 0 : n;
    }

    /** What a pathologist has signed off, newest first. */
    public List<VerificationRow> verifiedBy(UUID pathologistId, int limit, int offset) {
        return jdbc.query(VERIFICATION_SELECT + """
                WHERE r.status = 'VERIFIED' AND r.verified_by_pathologist_id = :id
                ORDER BY r.verified_at DESC LIMIT :limit OFFSET :offset
                """, new MapSqlParameterSource("id", pathologistId).addValue("limit", limit).addValue("offset", offset),
                VERIFICATION);
    }

    public long countVerifiedBy(UUID pathologistId) {
        Long n = jdbc.queryForObject("SELECT count(*) FROM sample_results WHERE status = 'VERIFIED' AND verified_by_pathologist_id = :id",
                new MapSqlParameterSource("id", pathologistId), Long.class);
        return n == null ? 0 : n;
    }

    public long countVerifiedBetween(UUID pathologistId, Instant from, Instant to) {
        Long n = jdbc.queryForObject("""
                SELECT count(*) FROM sample_results WHERE status = 'VERIFIED' AND verified_by_pathologist_id = :id
                  AND verified_at >= :from AND verified_at < :to
                """, new MapSqlParameterSource("id", pathologistId).addValue("from", Timestamp.from(from))
                .addValue("to", Timestamp.from(to)), Long.class);
        return n == null ? 0 : n;
    }

    public long countReturnedBetween(UUID pathologistId, Instant from, Instant to) {
        Long n = jdbc.queryForObject("""
                SELECT count(*) FROM sample_results WHERE status = 'RETURNED_FOR_RETEST' AND returned_by_pathologist_id = :id
                  AND returned_at >= :from AND returned_at < :to
                """, new MapSqlParameterSource("id", pathologistId).addValue("from", Timestamp.from(from))
                .addValue("to", Timestamp.from(to)), Long.class);
        return n == null ? 0 : n;
    }

    /**
     * The patient's earlier verified numeric values for these parameters, newest first, at most
     * {@code perParameter} each — the trend behind today's value.
     */
    public Map<UUID, List<TrendPoint>> trend(UUID patientId, Collection<UUID> parameterIds, UUID excludeSampleId, int perParameter) {
        Map<UUID, List<TrendPoint>> result = new HashMap<>();
        if (parameterIds.isEmpty()) {
            return result;
        }
        jdbc.query("""
                SELECT parameter_id, numeric_value, verified_at FROM (
                    SELECT v.parameter_id, v.numeric_value, r.verified_at,
                           row_number() OVER (PARTITION BY v.parameter_id ORDER BY r.verified_at DESC) AS rn
                    FROM result_values v
                    JOIN sample_results r ON r.id = v.sample_result_id AND r.status = 'VERIFIED'
                    JOIN samples s ON s.id = r.sample_id
                    WHERE s.patient_id = :patientId AND s.id <> :exclude AND v.parameter_id IN (:parameterIds)
                      AND v.numeric_value IS NOT NULL
                ) ranked WHERE rn <= :per ORDER BY parameter_id, verified_at DESC
                """, new MapSqlParameterSource("patientId", patientId).addValue("exclude", excludeSampleId)
                .addValue("parameterIds", parameterIds).addValue("per", perParameter), rs -> {
            result.computeIfAbsent(rs.getObject("parameter_id", UUID.class), k -> new java.util.ArrayList<>())
                    .add(new TrendPoint(rs.getBigDecimal("numeric_value"), instant(rs, "verified_at")));
        });
        return result;
    }

    // ---------------------------------------------------------------- report lists

    private static final String REPORT_SELECT = """
            SELECT s.id AS sample_id, s.sample_code, o.id AS order_id, o.order_code, p.id AS patient_id, p.patient_code,
                   p.full_name AS patient_name, %s AS test_names, r.verified_at, rep.dispatched_at,
                   rep.dispatched_channel, rep.receipt_confirmed_at,
                   EXISTS (SELECT 1 FROM result_values v WHERE v.sample_result_id = r.id
                           AND v.flag IN ('CRITICAL_LOW', 'CRITICAL_HIGH')) AS critical,
                   EXISTS (SELECT 1 FROM result_values v WHERE v.sample_result_id = r.id
                           AND v.flag IS NOT NULL AND v.flag <> 'NORMAL') AS abnormal
            FROM reports rep
            JOIN samples s ON s.id = rep.sample_id
            JOIN sample_results r ON r.sample_id = s.id AND r.status = 'VERIFIED'
            JOIN lab_orders o ON o.id = s.lab_order_id
            JOIN patients p ON p.id = s.patient_id
            """.formatted(TEST_NAMES);

    private static final RowMapper<ReportRow> REPORT = (rs, i) -> new ReportRow(
            rs.getObject("sample_id", UUID.class), rs.getString("sample_code"), rs.getObject("order_id", UUID.class),
            rs.getString("order_code"), rs.getObject("patient_id", UUID.class), rs.getString("patient_code"),
            rs.getString("patient_name"), strings(rs.getArray("test_names")), instant(rs, "verified_at"),
            instant(rs, "dispatched_at"),
            rs.getString("dispatched_channel") == null ? null : Report.Channel.valueOf(rs.getString("dispatched_channel")),
            instant(rs, "receipt_confirmed_at"), rs.getBoolean("critical"), rs.getBoolean("abnormal"));

    /** Reports the patient can open: dispatched to them, newest first. */
    public List<ReportRow> dispatchedForPatient(UUID patientId, int limit) {
        return jdbc.query(REPORT_SELECT + """
                WHERE p.id = :patientId AND rep.dispatched_at IS NOT NULL
                ORDER BY rep.dispatched_at DESC LIMIT :limit
                """, new MapSqlParameterSource("patientId", patientId).addValue("limit", limit), REPORT);
    }

    /** Verified reports for orders this doctor placed, newest first (dispatched or not). */
    public List<ReportRow> orderedBy(UUID doctorId, int limit, int offset) {
        return jdbc.query(REPORT_SELECT + """
                WHERE o.ordering_doctor_id = :doctorId
                ORDER BY r.verified_at DESC LIMIT :limit OFFSET :offset
                """, new MapSqlParameterSource("doctorId", doctorId).addValue("limit", limit).addValue("offset", offset), REPORT);
    }

    public long countOrderedBy(UUID doctorId) {
        Long n = jdbc.queryForObject("""
                SELECT count(*) FROM reports rep JOIN samples s ON s.id = rep.sample_id
                JOIN lab_orders o ON o.id = s.lab_order_id WHERE o.ordering_doctor_id = :doctorId
                """, new MapSqlParameterSource("doctorId", doctorId), Long.class);
        return n == null ? 0 : n;
    }

    /** Verified reports that have not been sent to the patient yet, oldest first. */
    public List<ReportRow> toDispatch(int limit, int offset) {
        return jdbc.query(REPORT_SELECT + """
                WHERE rep.dispatched_at IS NULL
                ORDER BY critical DESC, r.verified_at LIMIT :limit OFFSET :offset
                """, new MapSqlParameterSource("limit", limit).addValue("offset", offset), REPORT);
    }

    public long countToDispatch() {
        Long n = jdbc.queryForObject("SELECT count(*) FROM reports WHERE dispatched_at IS NULL",
                new MapSqlParameterSource(), Long.class);
        return n == null ? 0 : n;
    }

    public long countReports(UUID patientId) {
        Long n = jdbc.queryForObject("""
                SELECT count(*) FROM reports rep JOIN samples s ON s.id = rep.sample_id
                WHERE s.patient_id = :patientId AND rep.dispatched_at IS NOT NULL
                """, new MapSqlParameterSource("patientId", patientId), Long.class);
        return n == null ? 0 : n;
    }

    // ---------------------------------------------------------------- trends across visits

    /** One verified numeric value with the test and parameter it belongs to. */
    public record TrendRow(UUID parameterId, String parameterName, String unit, String testCode, String testName,
                           java.math.BigDecimal value, RangeCheck.Flag flag, java.math.BigDecimal refLow,
                           java.math.BigDecimal refHigh, java.math.BigDecimal criticalLow, java.math.BigDecimal criticalHigh,
                           UUID sampleId, String sampleCode, Instant verifiedAt) {
    }

    /**
     * Every verified numeric value for the patient, grouped by test then parameter and oldest first within each.
     * {@code dispatchedOnly} limits it to reports already sent to the patient (what the patient may see).
     */
    public List<TrendRow> trendRows(UUID patientId, boolean dispatchedOnly) {
        return jdbc.query("""
                SELECT v.parameter_id, v.parameter_name, v.unit, t.code AS test_code, t.name AS test_name, v.numeric_value,
                       v.flag, v.ref_low, v.ref_high, v.critical_low, v.critical_high, s.id AS sample_id, s.sample_code,
                       r.verified_at
                FROM result_values v
                JOIN sample_results r ON r.id = v.sample_result_id AND r.status = 'VERIFIED'
                JOIN samples s ON s.id = r.sample_id
                JOIN lab_order_items i ON i.id = v.lab_order_item_id
                JOIN lab_tests t ON t.id = i.lab_test_id
                JOIN reports rep ON rep.sample_id = s.id
                WHERE s.patient_id = :patientId AND v.numeric_value IS NOT NULL
                  AND (NOT :dispatchedOnly OR rep.dispatched_at IS NOT NULL)
                ORDER BY t.name, v.parameter_name, v.parameter_id, r.verified_at
                """, new MapSqlParameterSource("patientId", patientId).addValue("dispatchedOnly", dispatchedOnly),
                (rs, i) -> new TrendRow(rs.getObject("parameter_id", UUID.class), rs.getString("parameter_name"), rs.getString("unit"),
                        rs.getString("test_code"), rs.getString("test_name"), rs.getBigDecimal("numeric_value"),
                        rs.getString("flag") == null ? null : RangeCheck.Flag.valueOf(rs.getString("flag")),
                        rs.getBigDecimal("ref_low"), rs.getBigDecimal("ref_high"), rs.getBigDecimal("critical_low"),
                        rs.getBigDecimal("critical_high"), rs.getObject("sample_id", UUID.class), rs.getString("sample_code"),
                        instant(rs, "verified_at")));
    }

    // ---------------------------------------------------------------- critical alerts

    /** Unacknowledged critical reports, longest waiting first; {@code doctorId} narrows it to one doctor's orders. */
    public List<ResultDtos.CriticalAlert> criticalOpen(UUID doctorId, int limit) {
        return jdbc.query("""
                SELECT s.id AS sample_id, s.sample_code, p.id AS patient_id, p.patient_code, p.full_name AS patient_name,
                       d.full_name AS doctor_name, %s AS test_names, r.verified_at,
                       COALESCE((SELECT array_agg(v.parameter_name || '|' || v.flag ORDER BY v.position)
                                 FROM result_values v WHERE v.sample_result_id = r.id
                                   AND v.flag IN ('CRITICAL_LOW', 'CRITICAL_HIGH')), '{}') AS critical_values
                FROM reports rep
                JOIN samples s ON s.id = rep.sample_id
                JOIN lab_orders o ON o.id = s.lab_order_id
                JOIN sample_results r ON r.sample_id = s.id AND r.status = 'VERIFIED'
                JOIN patients p ON p.id = s.patient_id
                JOIN doctors d ON d.id = o.ordering_doctor_id
                WHERE rep.is_critical AND rep.critical_acknowledged_at IS NULL
                  AND (CAST(:doctorId AS uuid) IS NULL OR o.ordering_doctor_id = CAST(:doctorId AS uuid))
                ORDER BY r.verified_at LIMIT :limit
                """.formatted(TEST_NAMES),
                new MapSqlParameterSource("doctorId", doctorId).addValue("limit", limit),
                (rs, i) -> new ResultDtos.CriticalAlert(rs.getObject("sample_id", UUID.class), rs.getString("sample_code"),
                        rs.getObject("patient_id", UUID.class), rs.getString("patient_code"), rs.getString("patient_name"),
                        rs.getString("doctor_name"), strings(rs.getArray("test_names")), criticalParameters(rs.getArray("critical_values")),
                        instant(rs, "verified_at")));
    }

    public long countCriticalOpen(UUID doctorId) {
        Long n = jdbc.queryForObject("""
                SELECT count(*) FROM reports rep JOIN samples s ON s.id = rep.sample_id
                JOIN lab_orders o ON o.id = s.lab_order_id
                WHERE rep.is_critical AND rep.critical_acknowledged_at IS NULL
                  AND (CAST(:doctorId AS uuid) IS NULL OR o.ordering_doctor_id = CAST(:doctorId AS uuid))
                """, new MapSqlParameterSource("doctorId", doctorId), Long.class);
        return n == null ? 0 : n;
    }

    /** When the longest-waiting unacknowledged critical result was verified. */
    public Instant oldestCriticalOpen() {
        Timestamp t = jdbc.queryForObject("""
                SELECT min(r.verified_at) FROM reports rep
                JOIN sample_results r ON r.sample_id = rep.sample_id AND r.status = 'VERIFIED'
                WHERE rep.is_critical AND rep.critical_acknowledged_at IS NULL
                """, new MapSqlParameterSource(), Timestamp.class);
        return t == null ? null : t.toInstant();
    }

    private static List<ResultDtos.CriticalParameter> criticalParameters(Array array) throws SQLException {
        return strings(array).stream().map(s -> {
            int bar = s.lastIndexOf('|');
            return new ResultDtos.CriticalParameter(s.substring(0, bar), RangeCheck.Flag.valueOf(s.substring(bar + 1)));
        }).toList();
    }

    // ---------------------------------------------------------------- authenticity

    /** What a scan of a report's QR code may show: enough to confirm the clinic issued it, no results. */
    public record Authenticity(String sampleCode, String patientName, List<String> testNames, Instant verifiedAt,
                               String verifierName, String qualification, String registrationNumber) {
    }

    public java.util.Optional<Authenticity> authenticity(String verificationCode) {
        return jdbc.query("""
                SELECT s.sample_code, p.full_name AS patient_name, %s AS test_names, r.verified_at,
                       pa.full_name AS verifier_name, pa.qualification, pa.registration_number
                FROM reports rep
                JOIN samples s ON s.id = rep.sample_id
                JOIN sample_results r ON r.sample_id = s.id AND r.status = 'VERIFIED'
                JOIN patients p ON p.id = s.patient_id
                JOIN pathologists pa ON pa.id = r.verified_by_pathologist_id
                WHERE rep.verification_code = :code
                """.formatted(TEST_NAMES), new MapSqlParameterSource("code", verificationCode),
                (rs, i) -> new Authenticity(rs.getString("sample_code"), rs.getString("patient_name"),
                        strings(rs.getArray("test_names")), instant(rs, "verified_at"), rs.getString("verifier_name"),
                        rs.getString("qualification"), rs.getString("registration_number"))).stream().findFirst();
    }

    // ---------------------------------------------------------------- helpers

    private static Instant instant(java.sql.ResultSet rs, String column) throws SQLException {
        Timestamp t = rs.getTimestamp(column);
        return t == null ? null : t.toInstant();
    }

    private static List<String> strings(Array array) throws SQLException {
        return array == null ? List.of() : Arrays.stream((Object[]) array.getArray()).map(String::valueOf).toList();
    }
}
