package com.cdlms.sample;

import com.cdlms.lab.LabOrder;
import com.cdlms.lab.TubeType;
import com.cdlms.sample.SampleDtos.SampleSummary;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Array;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Read-only list queries (one SQL each) behind the lab's sample queues and dashboards. */
@Repository
public class SampleQueries {

    public record UserRef(String name, String role) {
    }

    private static final String SUMMARY_SELECT = """
            SELECT s.id, s.sample_code, s.status, s.required_tube_type, s.tube_mismatch,
                   s.redraw_of_sample_id IS NOT NULL AS redraw, s.created_at, s.collected_at,
                   o.id AS order_id, o.order_code, o.priority,
                   p.id AS patient_id, p.patient_code, p.full_name AS patient_name,
                   COALESCE((SELECT array_agg(i.test_name ORDER BY i.created_at)
                             FROM sample_items si JOIN lab_order_items i ON i.id = si.lab_order_item_id
                             WHERE si.sample_id = s.id), '{}') AS test_names
            FROM samples s
            JOIN lab_orders o ON o.id = s.lab_order_id
            JOIN patients p ON p.id = s.patient_id
            """;

    private static final RowMapper<SampleSummary> SUMMARY = (rs, n) -> new SampleSummary(
            rs.getObject("id", UUID.class), rs.getString("sample_code"), SampleStatus.valueOf(rs.getString("status")),
            TubeType.valueOf(rs.getString("required_tube_type")), rs.getBoolean("tube_mismatch"), rs.getBoolean("redraw"),
            rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("collected_at") == null ? null : rs.getTimestamp("collected_at").toInstant(),
            rs.getObject("order_id", UUID.class), rs.getString("order_code"),
            LabOrder.Priority.valueOf(rs.getString("priority")), rs.getObject("patient_id", UUID.class),
            rs.getString("patient_code"), rs.getString("patient_name"), strings(rs.getArray("test_names")));

    private final NamedParameterJdbcTemplate jdbc;

    public SampleQueries(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Samples waiting at one step: {@code ORDERED} (to collect) or {@code COLLECTED} (to receive). Urgent
     * orders first, then redraws (the patient is waiting for them), then oldest first.
     */
    public List<SampleSummary> waiting(SampleStatus status, int limit, int offset) {
        return jdbc.query(SUMMARY_SELECT + """
                WHERE s.status = :status
                ORDER BY (o.priority = 'URGENT') DESC, (s.redraw_of_sample_id IS NOT NULL) DESC, s.created_at
                LIMIT :limit OFFSET :offset
                """, new MapSqlParameterSource("status", status.name()).addValue("limit", limit).addValue("offset", offset),
                SUMMARY);
    }

    public long countWaiting(SampleStatus status) {
        Long n = jdbc.queryForObject("SELECT count(*) FROM samples WHERE status = :status",
                new MapSqlParameterSource("status", status.name()), Long.class);
        return n == null ? 0 : n;
    }

    /** Samples waiting to be collected that belong to an urgent order. */
    public long countUrgentToCollect() {
        Long n = jdbc.queryForObject("""
                SELECT count(*) FROM samples s JOIN lab_orders o ON o.id = s.lab_order_id
                WHERE s.status = 'ORDERED' AND o.priority = 'URGENT'
                """, new MapSqlParameterSource(), Long.class);
        return n == null ? 0 : n;
    }

    /** How many tubes of each kind still have to be drawn — what to set out before collection starts. */
    public Map<String, Long> tubesToCollect() {
        Map<String, Long> counts = new LinkedHashMap<>();
        jdbc.query("""
                SELECT required_tube_type AS tube, count(*) AS n FROM samples
                WHERE status = 'ORDERED' GROUP BY required_tube_type ORDER BY n DESC, tube
                """, new MapSqlParameterSource(), rs -> {
            counts.put(rs.getString("tube"), rs.getLong("n"));
        });
        return counts;
    }

    /** Display name and role for each user id (staff, doctors, pathologists), for the chain-of-custody log. */
    public Map<UUID, UserRef> users(Collection<UUID> ids) {
        Map<UUID, UserRef> result = new HashMap<>();
        if (ids.isEmpty()) {
            return result;
        }
        jdbc.query("""
                SELECT u.id, COALESCE(d.full_name, pa.full_name, s.full_name, u.email) AS name, u.role
                FROM users u
                LEFT JOIN doctors d ON d.user_id = u.id
                LEFT JOIN pathologists pa ON pa.user_id = u.id
                LEFT JOIN staff s ON s.user_id = u.id
                WHERE u.id IN (:ids)
                """, new MapSqlParameterSource("ids", ids), rs -> {
            result.put(rs.getObject("id", UUID.class), new UserRef(rs.getString("name"), rs.getString("role")));
        });
        return result;
    }

    private static List<String> strings(Array array) throws SQLException {
        return array == null ? List.of() : Arrays.stream((Object[]) array.getArray()).map(String::valueOf).toList();
    }
}
