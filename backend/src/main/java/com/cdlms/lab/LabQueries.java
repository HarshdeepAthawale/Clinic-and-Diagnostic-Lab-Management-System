package com.cdlms.lab;

import com.cdlms.lab.LabDtos.LabOrderSummary;
import com.cdlms.lab.LabDtos.LabTestSummary;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Array;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/** Read-only list queries (one SQL each) behind the catalog picker, the lab's order queue and order histories. */
@Repository
public class LabQueries {

    private static final RowMapper<LabTestSummary> TEST = (rs, i) -> new LabTestSummary(
            rs.getObject("id", UUID.class), rs.getString("code"), rs.getString("name"), rs.getString("category"),
            SampleType.valueOf(rs.getString("sample_type")), TubeType.valueOf(rs.getString("required_tube_type")),
            rs.getBigDecimal("price"), rs.getInt("turnaround_hours"), rs.getString("prep_instructions"),
            rs.getBoolean("is_active"), rs.getInt("parameter_count"));

    /** Orders with their live tests folded into arrays, so a list is one query however many tests each has. */
    private static final String ORDER_SELECT = """
            SELECT o.id, o.order_code, o.status, o.priority, o.created_at, o.consultation_id,
                   p.id AS patient_id, p.patient_code, p.full_name AS patient_name, d.full_name AS doctor_name,
                   count(i.id) AS test_count,
                   COALESCE(array_agg(i.test_name ORDER BY i.created_at) FILTER (WHERE i.id IS NOT NULL), '{}') AS test_names,
                   COALESCE(array_agg(DISTINCT t.required_tube_type) FILTER (WHERE i.id IS NOT NULL), '{}') AS tubes,
                   COALESCE(sum(i.price_at_order), 0) AS total,
                   bool_or(t.prep_instructions IS NOT NULL) AS has_prep
            FROM lab_orders o
            JOIN patients p ON p.id = o.patient_id
            JOIN doctors d ON d.id = o.ordering_doctor_id
            LEFT JOIN lab_order_items i ON i.lab_order_id = o.id
                 AND (i.status <> 'CANCELLED' OR o.status = 'CANCELLED')
            LEFT JOIN lab_tests t ON t.id = i.lab_test_id
            """;

    private static final String ORDER_GROUP = """
            GROUP BY o.id, p.id, d.id
            """;

    private static final RowMapper<LabOrderSummary> ORDER = (rs, i) -> new LabOrderSummary(
            rs.getObject("id", UUID.class), rs.getString("order_code"), LabOrder.Status.valueOf(rs.getString("status")),
            LabOrder.Priority.valueOf(rs.getString("priority")), rs.getTimestamp("created_at").toInstant(),
            rs.getObject("consultation_id", UUID.class), rs.getObject("patient_id", UUID.class),
            rs.getString("patient_code"), rs.getString("patient_name"), rs.getString("doctor_name"),
            rs.getInt("test_count"), strings(rs.getArray("test_names")),
            strings(rs.getArray("tubes")).stream().map(TubeType::valueOf).toList(),
            rs.getBigDecimal("total"), rs.getBoolean("has_prep"));

    private final NamedParameterJdbcTemplate jdbc;

    public LabQueries(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ---------------------------------------------------------------- catalog

    /**
     * Catalog entries matching a name/code/category search (empty = all), name-prefix matches first.
     * Inactive tests are only included for the admin's catalog screen.
     */
    public List<LabTestSummary> tests(String query, boolean includeInactive) {
        String q = query == null ? "" : query.trim().toLowerCase();
        return jdbc.query("""
                SELECT t.id, t.code, t.name, t.category, t.sample_type, t.required_tube_type, t.price,
                       t.turnaround_hours, t.prep_instructions, t.is_active,
                       (SELECT count(*) FROM lab_test_parameters p WHERE p.lab_test_id = t.id) AS parameter_count
                FROM lab_tests t
                WHERE (:includeInactive OR t.is_active)
                  AND (:q = '' OR lower(t.name) LIKE :contains OR lower(t.code) LIKE :contains
                       OR lower(t.category) LIKE :contains)
                ORDER BY (:q <> '' AND (lower(t.name) LIKE :prefix OR lower(t.code) LIKE :prefix)) DESC,
                         t.category, t.name
                """, new MapSqlParameterSource("q", q).addValue("contains", "%" + q + "%")
                .addValue("prefix", q + "%").addValue("includeInactive", includeInactive), TEST);
    }

    // ---------------------------------------------------------------- orders

    /** The lab's queue: open orders, urgent first, then oldest first. */
    public List<LabOrderSummary> openOrders(int limit, int offset) {
        return jdbc.query(ORDER_SELECT + "WHERE o.status = 'ORDERED'\n" + ORDER_GROUP + """
                ORDER BY (o.priority = 'URGENT') DESC, o.created_at
                LIMIT :limit OFFSET :offset
                """, new MapSqlParameterSource("limit", limit).addValue("offset", offset), ORDER);
    }

    public long countOpenOrders() {
        Long n = jdbc.queryForObject("SELECT count(*) FROM lab_orders WHERE status = 'ORDERED'",
                new MapSqlParameterSource(), Long.class);
        return n == null ? 0 : n;
    }

    public long countOpenUrgent() {
        Long n = jdbc.queryForObject("SELECT count(*) FROM lab_orders WHERE status = 'ORDERED' AND priority = 'URGENT'",
                new MapSqlParameterSource(), Long.class);
        return n == null ? 0 : n;
    }

    /** A patient's orders, newest first (cancelled ones included, for the record). */
    public List<LabOrderSummary> forPatient(UUID patientId, int limit) {
        return jdbc.query(ORDER_SELECT + "WHERE o.patient_id = :patientId\n" + ORDER_GROUP + """
                ORDER BY o.created_at DESC LIMIT :limit
                """, new MapSqlParameterSource("patientId", patientId).addValue("limit", limit), ORDER);
    }

    private static List<String> strings(Array array) throws SQLException {
        if (array == null) {
            return List.of();
        }
        Object[] values = (Object[]) array.getArray();
        return Arrays.stream(values).map(String::valueOf).toList();
    }
}
