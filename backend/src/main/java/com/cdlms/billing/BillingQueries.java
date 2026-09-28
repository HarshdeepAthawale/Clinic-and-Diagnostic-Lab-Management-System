package com.cdlms.billing;

import com.cdlms.billing.BillingDtos.Collections;
import com.cdlms.billing.BillingDtos.InvoiceSummary;
import com.cdlms.billing.BillingDtos.PaymentView;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Read-only list and total queries (one SQL each) behind the billing counter, histories and dashboards. */
@Repository
public class BillingQueries {

    /** Which invoices a list shows. */
    public enum Filter { OUTSTANDING, PAID, ALL }

    /** A staff member's display name, whatever their role. */
    static final String USER_NAME = "COALESCE(d.full_name, pa.full_name, s.full_name, u.email)";

    private static final String SUMMARY_SELECT = """
            SELECT i.id, i.invoice_code, i.status, i.created_at, p.id AS patient_id, p.patient_code,
                   p.full_name AS patient_name, doc.full_name AS doctor_name, i.lab_order_id IS NOT NULL AS lab_only,
                   (SELECT count(*) FROM invoice_items l WHERE l.invoice_id = i.id AND l.voided_at IS NULL) AS line_count,
                   i.consultation_fee + i.test_charges_total - i.discount AS net, i.amount_paid
            FROM invoices i
            JOIN patients p ON p.id = i.patient_id
            LEFT JOIN consultations c ON c.id = i.consultation_id
            LEFT JOIN lab_orders o ON o.id = i.lab_order_id
            LEFT JOIN doctors doc ON doc.id = COALESCE(c.doctor_id, o.ordering_doctor_id)
            """;

    private static final RowMapper<InvoiceSummary> SUMMARY = (rs, i) -> {
        BigDecimal net = rs.getBigDecimal("net");
        BigDecimal paid = rs.getBigDecimal("amount_paid");
        return new InvoiceSummary(rs.getObject("id", UUID.class), rs.getString("invoice_code"),
                Invoice.Status.valueOf(rs.getString("status")), rs.getTimestamp("created_at").toInstant(),
                rs.getObject("patient_id", UUID.class), rs.getString("patient_code"), rs.getString("patient_name"),
                rs.getString("doctor_name"), rs.getBoolean("lab_only"), rs.getInt("line_count"), net, paid,
                net.subtract(paid));
    };

    private static final String FILTERS = """
            WHERE (:filter = 'ALL' AND i.status <> 'VOID'
                   OR :filter = 'OUTSTANDING' AND i.status IN ('UNPAID', 'PARTIALLY_PAID')
                   OR :filter = 'PAID' AND i.status = 'PAID')
              AND (:q = '' OR lower(p.full_name) LIKE :contains OR lower(p.patient_code) LIKE :contains
                   OR lower(i.invoice_code) LIKE :contains)
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public BillingQueries(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static MapSqlParameterSource filterParams(Filter filter, String query) {
        String q = query == null ? "" : query.trim().toLowerCase();
        return new MapSqlParameterSource("filter", filter.name()).addValue("q", q).addValue("contains", "%" + q + "%");
    }

    /** The counter's list: outstanding bills oldest first (they've waited longest), others newest first. */
    public List<InvoiceSummary> list(Filter filter, String query, int limit, int offset) {
        String order = filter == Filter.OUTSTANDING ? "i.created_at" : "i.created_at DESC";
        return jdbc.query(SUMMARY_SELECT + FILTERS + "ORDER BY " + order + " LIMIT :limit OFFSET :offset",
                filterParams(filter, query).addValue("limit", limit).addValue("offset", offset), SUMMARY);
    }

    public long count(Filter filter, String query) {
        Long n = jdbc.queryForObject("SELECT count(*) FROM invoices i JOIN patients p ON p.id = i.patient_id\n" + FILTERS,
                filterParams(filter, query), Long.class);
        return n == null ? 0 : n;
    }

    /** A patient's invoices, newest first (void ones left out). */
    public List<InvoiceSummary> forPatient(UUID patientId, int limit) {
        return jdbc.query(SUMMARY_SELECT + """
                WHERE i.patient_id = :patientId AND i.status <> 'VOID'
                ORDER BY i.created_at DESC LIMIT :limit
                """, new MapSqlParameterSource("patientId", patientId).addValue("limit", limit), SUMMARY);
    }

    /** A patient's unpaid and part-paid invoices, for their dashboard. */
    public List<InvoiceSummary> outstandingForPatient(UUID patientId, int limit) {
        return jdbc.query(SUMMARY_SELECT + """
                WHERE i.patient_id = :patientId AND i.status IN ('UNPAID', 'PARTIALLY_PAID')
                ORDER BY i.created_at DESC LIMIT :limit
                """, new MapSqlParameterSource("patientId", patientId).addValue("limit", limit), SUMMARY);
    }

    public List<PaymentView> payments(UUID invoiceId) {
        return jdbc.query("""
                SELECT pm.id, pm.amount, pm.method, pm.reference, pm.received_at, %s AS received_by
                FROM payments pm
                JOIN users u ON u.id = pm.received_by_user_id
                LEFT JOIN doctors d ON d.user_id = u.id
                LEFT JOIN pathologists pa ON pa.user_id = u.id
                LEFT JOIN staff s ON s.user_id = u.id
                WHERE pm.invoice_id = :invoiceId
                ORDER BY pm.received_at
                """.formatted(USER_NAME), new MapSqlParameterSource("invoiceId", invoiceId),
                (rs, i) -> new PaymentView(rs.getObject("id", UUID.class), rs.getBigDecimal("amount"),
                        Payment.Method.valueOf(rs.getString("method")), rs.getString("reference"),
                        rs.getString("received_by"), rs.getTimestamp("received_at").toInstant()));
    }

    public record UserRef(String name, String role) {
    }

    /** Display name and role of a user (who applied a discount). */
    public UserRef user(UUID userId) {
        return jdbc.queryForObject("""
                SELECT %s AS name, u.role FROM users u
                LEFT JOIN doctors d ON d.user_id = u.id
                LEFT JOIN pathologists pa ON pa.user_id = u.id
                LEFT JOIN staff s ON s.user_id = u.id
                WHERE u.id = :id
                """.formatted(USER_NAME), new MapSqlParameterSource("id", userId),
                (rs, i) -> new UserRef(rs.getString("name"), rs.getString("role")));
    }

    // ---------------------------------------------------------------- dashboard totals

    public long countOutstanding() {
        Long n = jdbc.queryForObject("SELECT count(*) FROM invoices WHERE status IN ('UNPAID', 'PARTIALLY_PAID')",
                new MapSqlParameterSource(), Long.class);
        return n == null ? 0 : n;
    }

    public BigDecimal outstandingAmount() {
        BigDecimal v = jdbc.queryForObject("""
                SELECT COALESCE(sum(consultation_fee + test_charges_total - discount - amount_paid), 0)
                FROM invoices WHERE status IN ('UNPAID', 'PARTIALLY_PAID')
                """, new MapSqlParameterSource(), BigDecimal.class);
        return v == null ? BigDecimal.ZERO : v;
    }

    public Collections collectedBetween(Instant from, Instant to) {
        return jdbc.queryForObject("""
                SELECT COALESCE(sum(amount), 0) AS total,
                       COALESCE(sum(amount) FILTER (WHERE method = 'CASH'), 0) AS cash,
                       COALESCE(sum(amount) FILTER (WHERE method = 'CARD'), 0) AS card,
                       COALESCE(sum(amount) FILTER (WHERE method = 'UPI'), 0) AS upi,
                       count(*) AS n
                FROM payments WHERE received_at >= :from AND received_at < :to
                """, new MapSqlParameterSource("from", Timestamp.from(from)).addValue("to", Timestamp.from(to)),
                (rs, i) -> new Collections(rs.getBigDecimal("total"), rs.getBigDecimal("cash"),
                        rs.getBigDecimal("card"), rs.getBigDecimal("upi"), rs.getLong("n")));
    }
}
