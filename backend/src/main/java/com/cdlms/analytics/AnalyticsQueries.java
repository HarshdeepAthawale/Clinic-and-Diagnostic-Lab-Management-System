package com.cdlms.analytics;

import com.cdlms.analytics.AnalyticsDtos.HeatCell;
import com.cdlms.analytics.AnalyticsDtos.TatRow;
import com.cdlms.analytics.AnalyticsDtos.TopTest;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Read-only aggregate SQL behind the admin insights. Days are the clinic's days: every grouping converts
 * to the clinic time zone first. Periods are half-open ({@code from} inclusive, {@code to} exclusive).
 */
@Repository
public class AnalyticsQueries {

    /** Turnaround per sample, from the sample's own event log: collected → received → result → verified → reported. */
    private static final String SAMPLE_TIMES = """
            WITH ev AS (
                SELECT sample_id,
                       min(occurred_at) FILTER (WHERE status = 'COLLECTED')        AS collected_at,
                       min(occurred_at) FILTER (WHERE status = 'RECEIVED_AT_LAB')  AS received_at,
                       max(occurred_at) FILTER (WHERE status = 'RESULT_ENTERED')   AS result_at,
                       max(occurred_at) FILTER (WHERE status = 'VERIFIED')         AS verified_at,
                       min(occurred_at) FILTER (WHERE status = 'REPORT_GENERATED') AS reported_at
                FROM sample_status_events GROUP BY sample_id
            ), done AS (
                SELECT ev.sample_id, ev.reported_at,
                       EXTRACT(EPOCH FROM (reported_at - collected_at)) / 60 AS total_min,
                       EXTRACT(EPOCH FROM (received_at - collected_at)) / 60 AS to_lab_min,
                       EXTRACT(EPOCH FROM (result_at - received_at)) / 60    AS testing_min,
                       EXTRACT(EPOCH FROM (verified_at - result_at)) / 60    AS verify_min,
                       EXISTS (SELECT 1 FROM sample_results r WHERE r.sample_id = ev.sample_id
                               AND r.status = 'RETURNED_FOR_RETEST')          AS retested
                FROM ev
                WHERE reported_at IS NOT NULL AND collected_at IS NOT NULL
                  AND reported_at >= :from AND reported_at < :to
            )
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public AnalyticsQueries(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static MapSqlParameterSource period(Instant from, Instant to, String zone) {
        return new MapSqlParameterSource("from", Timestamp.from(from)).addValue("to", Timestamp.from(to)).addValue("zone", zone);
    }

    // ---------------------------------------------------------------- per-day series

    /** Patients seen per day: distinct patients whose consultation was finished that day. */
    public Map<LocalDate, Long> patientsPerDay(Instant from, Instant to, String zone) {
        return counts("""
                SELECT (completed_at AT TIME ZONE :zone)::date AS day, count(DISTINCT patient_id) AS n
                FROM consultations WHERE status = 'COMPLETED' AND completed_at >= :from AND completed_at < :to
                GROUP BY day""", period(from, to, zone));
    }

    public Map<LocalDate, Long> registrationsPerDay(Instant from, Instant to, String zone) {
        return counts("""
                SELECT (created_at AT TIME ZONE :zone)::date AS day, count(*) AS n
                FROM patients WHERE created_at >= :from AND created_at < :to GROUP BY day""", period(from, to, zone));
    }

    public Map<LocalDate, Long> reportsPerDay(Instant from, Instant to, String zone) {
        return counts("""
                SELECT (generated_at AT TIME ZONE :zone)::date AS day, count(*) AS n
                FROM reports WHERE generated_at >= :from AND generated_at < :to GROUP BY day""", period(from, to, zone));
    }

    /** Money actually received per day (payments), not what was billed. */
    public Map<LocalDate, BigDecimal> revenuePerDay(Instant from, Instant to, String zone) {
        Map<LocalDate, BigDecimal> result = new LinkedHashMap<>();
        jdbc.query("""
                SELECT (received_at AT TIME ZONE :zone)::date AS day, sum(amount) AS total
                FROM payments WHERE received_at >= :from AND received_at < :to GROUP BY day""", period(from, to, zone), rs -> {
            result.put(rs.getObject("day", LocalDate.class), rs.getBigDecimal("total"));
        });
        return result;
    }

    // ---------------------------------------------------------------- totals

    public long patientsSeen(Instant from, Instant to) {
        return one("""
                SELECT count(DISTINCT patient_id) FROM consultations
                WHERE status = 'COMPLETED' AND completed_at >= :from AND completed_at < :to""", period(from, to, "UTC"));
    }

    public BigDecimal revenue(Instant from, Instant to) {
        BigDecimal total = jdbc.queryForObject("SELECT COALESCE(sum(amount), 0) FROM payments WHERE received_at >= :from AND received_at < :to",
                period(from, to, "UTC"), BigDecimal.class);
        return total == null ? BigDecimal.ZERO : total;
    }

    public long reports(Instant from, Instant to) {
        return one("SELECT count(*) FROM reports WHERE generated_at >= :from AND generated_at < :to", period(from, to, "UTC"));
    }

    public long samplesInProgress() {
        return one("SELECT count(*) FROM samples WHERE status IN ('RECEIVED_AT_LAB', 'IN_TESTING', 'RESULT_ENTERED')",
                new MapSqlParameterSource());
    }

    /** Median minutes from collection to report ready across all samples reported in the period; null if none. */
    public Long medianTatMinutes(Instant from, Instant to) {
        Double median = jdbc.queryForObject(SAMPLE_TIMES + "SELECT percentile_cont(0.5) WITHIN GROUP (ORDER BY total_min) FROM done",
                period(from, to, "UTC"), Double.class);
        return median == null ? null : Math.round(median);
    }

    // ---------------------------------------------------------------- tests

    /** The most-ordered tests (live order lines, cancelled ones left out) with what they billed. */
    public List<TopTest> topTests(Instant from, Instant to, int limit) {
        return jdbc.query("""
                SELECT t.id, t.code, t.name, count(*) AS orders, sum(i.price_at_order) AS revenue
                FROM lab_order_items i JOIN lab_tests t ON t.id = i.lab_test_id
                WHERE i.status <> 'CANCELLED' AND i.created_at >= :from AND i.created_at < :to
                GROUP BY t.id, t.code, t.name ORDER BY orders DESC, t.name LIMIT :limit
                """, period(from, to, "UTC").addValue("limit", limit),
                (rs, i) -> new TopTest(rs.getObject("id", UUID.class), rs.getString("code"), rs.getString("name"),
                        rs.getLong("orders"), rs.getBigDecimal("revenue")));
    }

    public Boolean testExists(UUID testId) {
        return jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM lab_tests WHERE id = :id)",
                new MapSqlParameterSource("id", testId), Boolean.class);
    }

    // ---------------------------------------------------------------- turnaround

    /**
     * Turnaround by test type for samples whose report was produced in the period: count, average, median and
     * 90th percentile of collection → report ready, the average of each stage, and how many were retested.
     * {@code testId} narrows it to one test.
     */
    public List<TatRow> tat(Instant from, Instant to, UUID testId) {
        return jdbc.query(SAMPLE_TIMES + """
                SELECT t.id, t.code, t.name, count(DISTINCT d.sample_id) AS samples,
                       avg(total_min) AS avg_min,
                       percentile_cont(0.5) WITHIN GROUP (ORDER BY total_min) AS median_min,
                       percentile_cont(0.9) WITHIN GROUP (ORDER BY total_min) AS p90_min,
                       avg(to_lab_min) AS to_lab, avg(testing_min) AS testing, avg(verify_min) AS verification,
                       count(*) FILTER (WHERE retested) AS retested
                FROM done d
                JOIN sample_items si ON si.sample_id = d.sample_id
                JOIN lab_order_items i ON i.id = si.lab_order_item_id
                JOIN lab_tests t ON t.id = i.lab_test_id
                WHERE (CAST(:testId AS uuid) IS NULL OR t.id = CAST(:testId AS uuid))
                GROUP BY t.id, t.code, t.name ORDER BY samples DESC, t.name
                """, period(from, to, "UTC").addValue("testId", testId),
                (rs, i) -> new TatRow(rs.getObject("id", UUID.class), rs.getString("code"), rs.getString("name"),
                        rs.getLong("samples"), rounded(rs, "avg_min"), rounded(rs, "median_min"), rounded(rs, "p90_min"),
                        nullableRounded(rs, "to_lab"), nullableRounded(rs, "testing"), nullableRounded(rs, "verification"),
                        rs.getLong("retested")));
    }

    /** Median turnaround per test per day (the day the report was produced), for the heatmap. */
    public List<HeatCell> heatmap(Instant from, Instant to, String zone, UUID testId) {
        return jdbc.query(SAMPLE_TIMES + """
                SELECT t.id, (d.reported_at AT TIME ZONE :zone)::date AS day,
                       percentile_cont(0.5) WITHIN GROUP (ORDER BY total_min) AS median_min,
                       count(DISTINCT d.sample_id) AS samples
                FROM done d
                JOIN sample_items si ON si.sample_id = d.sample_id
                JOIN lab_order_items i ON i.id = si.lab_order_item_id
                JOIN lab_tests t ON t.id = i.lab_test_id
                WHERE (CAST(:testId AS uuid) IS NULL OR t.id = CAST(:testId AS uuid))
                GROUP BY t.id, day ORDER BY day, t.id
                """, period(from, to, zone).addValue("testId", testId),
                (rs, i) -> new HeatCell(rs.getObject("id", UUID.class), rs.getObject("day", LocalDate.class),
                        rounded(rs, "median_min"), rs.getLong("samples")));
    }

    // ---------------------------------------------------------------- staff

    /** One row per (person, measure): what each staff member did in the period. */
    public List<Object[]> staffActions(Instant from, Instant to) {
        List<Object[]> rows = new ArrayList<>();
        jdbc.query("""
                SELECT user_id, measure, n FROM (
                    SELECT p.user_id AS user_id, 'Results verified' AS measure, count(*) AS n
                      FROM sample_results r JOIN pathologists p ON p.id = r.verified_by_pathologist_id
                      WHERE r.status = 'VERIFIED' AND r.verified_at >= :from AND r.verified_at < :to GROUP BY p.user_id
                    UNION ALL
                    SELECT p.user_id, 'Returned for retest', count(*)
                      FROM sample_results r JOIN pathologists p ON p.id = r.returned_by_pathologist_id
                      WHERE r.status = 'RETURNED_FOR_RETEST' AND r.returned_at >= :from AND r.returned_at < :to GROUP BY p.user_id
                    UNION ALL
                    SELECT collected_by_user_id, 'Samples collected', count(*) FROM samples
                      WHERE collected_at >= :from AND collected_at < :to GROUP BY collected_by_user_id
                    UNION ALL
                    SELECT received_by_user_id, 'Samples received', count(*) FROM samples
                      WHERE received_at >= :from AND received_at < :to GROUP BY received_by_user_id
                    UNION ALL
                    SELECT entered_by_user_id, 'Results entered', count(*) FROM sample_results
                      WHERE entered_at >= :from AND entered_at < :to GROUP BY entered_by_user_id
                    UNION ALL
                    SELECT registered_by_user_id, 'Patients registered', count(*) FROM patients
                      WHERE registered_by_user_id IS NOT NULL AND created_at >= :from AND created_at < :to GROUP BY registered_by_user_id
                    UNION ALL
                    SELECT received_by_user_id, 'Payments taken', count(*) FROM payments
                      WHERE received_at >= :from AND received_at < :to GROUP BY received_by_user_id
                    UNION ALL
                    SELECT d.user_id, 'Consultations', count(*)
                      FROM consultations c JOIN doctors d ON d.id = c.doctor_id
                      WHERE c.status = 'COMPLETED' AND c.completed_at >= :from AND c.completed_at < :to GROUP BY d.user_id
                ) x WHERE user_id IS NOT NULL
                """, period(from, to, "UTC"), rs -> {
            rows.add(new Object[] {rs.getObject("user_id", UUID.class), rs.getString("measure"), rs.getLong("n")});
        });
        return rows;
    }

    // ---------------------------------------------------------------- helpers

    private Map<LocalDate, Long> counts(String sql, MapSqlParameterSource params) {
        Map<LocalDate, Long> result = new LinkedHashMap<>();
        jdbc.query(sql, params, rs -> {
            result.put(rs.getObject("day", LocalDate.class), rs.getLong("n"));
        });
        return result;
    }

    private long one(String sql, MapSqlParameterSource params) {
        Long n = jdbc.queryForObject(sql, params, Long.class);
        return n == null ? 0 : n;
    }

    private static long rounded(ResultSet rs, String column) throws SQLException {
        return Math.round(rs.getDouble(column));
    }

    private static Long nullableRounded(ResultSet rs, String column) throws SQLException {
        double value = rs.getDouble(column);
        return rs.wasNull() ? null : Math.round(value);
    }
}
