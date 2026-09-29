package com.cdlms.sample;

import com.cdlms.support.IntegrationTest;
import com.cdlms.user.Role;
import com.cdlms.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V8 gives orders that pre-date samples their samples. Flyway has already run it on the empty test
 * database, so the test builds such an order by hand and runs the script again.
 */
class SampleBackfillTest extends IntegrationTest {

    private UUID doctorId;
    private UUID doctorUserId;
    private UUID patientId;

    @BeforeEach
    void setUp() {
        User doctorUser = testUsers.create(Role.DOCTOR);
        User patientUser = testUsers.create(Role.PATIENT);
        doctorUserId = doctorUser.getId();
        doctorId = jdbc.queryForObject("SELECT id FROM doctors WHERE user_id = ?", UUID.class, doctorUserId);
        patientId = jdbc.queryForObject("SELECT id FROM patients WHERE user_id = ?", UUID.class, patientUser.getId());
    }

    private void runBackfill() throws Exception {
        // Executed whole: the script is one DO block, which a semicolon-splitting runner would cut apart.
        jdbc.execute(new String(new ClassPathResource("db/migration/V8__backfill_samples.sql").getInputStream().readAllBytes(),
                StandardCharsets.UTF_8));
    }

    /** An open order made straight in the database, as one from before Phase 07 would be. */
    private UUID legacyOrder(String status, String... testCodes) {
        UUID orderId = jdbc.queryForObject("INSERT INTO lab_orders (patient_id, ordering_doctor_id, status) VALUES (?, ?, ?) RETURNING id",
                UUID.class, patientId, doctorId, status);
        for (String code : testCodes) {
            jdbc.update("INSERT INTO lab_order_items (lab_order_id, lab_test_id, test_name, price_at_order) "
                    + "SELECT ?, id, name, price FROM lab_tests WHERE code = ?", orderId, code);
        }
        return orderId;
    }

    @Test
    void anOpenOrderGetsOneSamplePerTubeWithACustodyEvent() throws Exception {
        UUID orderId = legacyOrder("ORDERED", "CBC", "ESR", "FBS");

        runBackfill();

        List<String> tubes = jdbc.queryForList("SELECT required_tube_type FROM samples WHERE lab_order_id = ? ORDER BY 1",
                String.class, orderId);
        assertThat(tubes).containsExactly("EDTA", "FLUORIDE");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM sample_items si JOIN samples s ON s.id = si.sample_id WHERE s.lab_order_id = ?",
                Long.class, orderId)).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM samples WHERE lab_order_id = ? AND status = 'ORDERED' "
                + "AND sample_code ~ '^LAB-[0-9]{8}-[0-9]{4}$'", Long.class, orderId)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM sample_status_events e JOIN samples s ON s.id = e.sample_id "
                + "WHERE s.lab_order_id = ? AND e.status = 'ORDERED' AND e.actor_user_id = ?", Long.class, orderId, doctorUserId))
                .isEqualTo(2);
    }

    @Test
    void runningItAgainAddsNothing() throws Exception {
        UUID orderId = legacyOrder("ORDERED", "CBC", "TSH");
        runBackfill();
        runBackfill();

        assertThat(jdbc.queryForObject("SELECT count(*) FROM samples WHERE lab_order_id = ?", Long.class, orderId)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(DISTINCT sample_code) FROM samples", Long.class))
                .isEqualTo(jdbc.queryForObject("SELECT count(*) FROM samples", Long.class));
    }

    @Test
    void cancelledOrdersAndCancelledTestsAreSkipped() throws Exception {
        UUID cancelled = legacyOrder("CANCELLED", "CBC");
        UUID partly = legacyOrder("ORDERED", "CBC", "TSH");
        jdbc.update("UPDATE lab_order_items SET status = 'CANCELLED', cancelled_at = now() WHERE lab_order_id = ? "
                + "AND test_name = (SELECT name FROM lab_tests WHERE code = 'TSH')", partly);

        runBackfill();

        assertThat(jdbc.queryForObject("SELECT count(*) FROM samples WHERE lab_order_id = ?", Long.class, cancelled)).isZero();
        assertThat(jdbc.queryForList("SELECT required_tube_type FROM samples WHERE lab_order_id = ?", String.class, partly))
                .containsExactly("EDTA");
    }
}
