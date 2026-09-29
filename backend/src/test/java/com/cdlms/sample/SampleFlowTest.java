package com.cdlms.sample;

import com.cdlms.auth.CsrfHeaderFilter;
import com.cdlms.support.IntegrationTest;
import com.cdlms.user.Role;
import com.cdlms.user.User;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Phase 07: sample creation, collection, receipt check, rejection and redraw (Rules.md §2, ADR-024). */
class SampleFlowTest extends IntegrationTest {

    private Cookie reception;
    private Cookie doctor;
    private Cookie patient;
    private Cookie otherPatient;
    private Cookie lab;
    private Cookie admin;
    private UUID doctorId;
    private UUID patientId;

    @BeforeEach
    void setUp() {
        reception = testUsers.loginCookie(testUsers.create(Role.RECEPTIONIST));
        User doctorUser = testUsers.create(Role.DOCTOR);
        doctor = testUsers.loginCookie(doctorUser);
        User patientUser = testUsers.create(Role.PATIENT);
        patient = testUsers.loginCookie(patientUser);
        otherPatient = testUsers.loginCookie(testUsers.create(Role.PATIENT, "other.patient@test.local"));
        lab = testUsers.loginCookie(testUsers.create(Role.LAB_TECHNICIAN));
        admin = testUsers.loginCookie(testUsers.create(Role.ADMIN));

        doctorId = jdbc.queryForObject("SELECT id FROM doctors WHERE user_id = ?", UUID.class, doctorUser.getId());
        patientId = jdbc.queryForObject("SELECT id FROM patients WHERE user_id = ?", UUID.class, patientUser.getId());
    }

    // ---------------------------------------------------------------- helpers

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder builder, String body) {
        return builder.header(CsrfHeaderFilter.HEADER, "1").contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private static String read(MvcResult result, String path) throws Exception {
        Object value = JsonPath.read(result.getResponse().getContentAsString(), path);
        return value == null ? null : value.toString();
    }

    private String ids(String... codes) {
        StringBuilder out = new StringBuilder("[");
        for (int i = 0; i < codes.length; i++) {
            UUID id = jdbc.queryForObject("SELECT id FROM lab_tests WHERE code = ?", UUID.class, codes[i]);
            out.append(i == 0 ? "" : ",").append('"').append(id).append('"');
        }
        return out.append(']').toString();
    }

    /** Checks the patient in and starts a consultation, giving the doctor a care relationship. */
    private String consultation() throws Exception {
        String appointmentId = read(mvc.perform(json(post("/api/queue/tokens"),
                        "{\"patientId\":\"" + patientId + "\",\"doctorId\":\"" + doctorId + "\"}").cookie(reception))
                .andExpect(status().isCreated()).andReturn(), "$.id");
        return read(mvc.perform(json(post("/api/consultations"), "{\"appointmentId\":\"" + appointmentId + "\"}")
                .cookie(doctor)).andExpect(status().isOk()).andReturn(), "$.id");
    }

    /** Orders tests directly for the patient; returns the order id. */
    private String order(String priority, String... codes) throws Exception {
        return read(mvc.perform(json(post("/api/lab-orders"), "{\"patientId\":\"" + patientId + "\",\"priority\":\""
                        + priority + "\",\"testIds\":" + ids(codes) + "}").cookie(doctor))
                .andExpect(status().isCreated()).andReturn(), "$.id");
    }

    private MvcResult samplesOf(String orderId, Cookie who) throws Exception {
        return mvc.perform(get("/api/lab-orders/" + orderId + "/samples").cookie(who)).andExpect(status().isOk()).andReturn();
    }

    /** The order's sample for one tube (by required tube type). */
    private String sampleFor(String orderId, String tube) throws Exception {
        return jdbc.queryForObject("SELECT id FROM samples WHERE lab_order_id = ? AND required_tube_type = ? "
                + "ORDER BY created_at DESC LIMIT 1", UUID.class, UUID.fromString(orderId), tube).toString();
    }

    private ResultActions collect(Cookie who, String sampleId, String tube, String site, boolean confirm) throws Exception {
        String body = "{\"tubeTypeUsed\":\"" + tube + "\"" + (site == null ? "" : ",\"bodySite\":\"" + site + "\"")
                + ",\"confirmMismatch\":" + confirm + "}";
        return mvc.perform(json(post("/api/samples/" + sampleId + "/collect"), body).cookie(who));
    }

    private ResultActions receive(Cookie who, String sampleId, String body) throws Exception {
        return mvc.perform(json(post("/api/samples/" + sampleId + "/receive"), body).cookie(who));
    }

    private String statusOf(String sampleId) {
        return jdbc.queryForObject("SELECT status FROM samples WHERE id = ?", String.class, UUID.fromString(sampleId));
    }

    // ---------------------------------------------------------------- samples are created by ordering

    @Test
    void testsSharingATubeShareOneSample() throws Exception {
        consultation();
        String orderId = order("ROUTINE", "CBC", "ESR", "FBS"); // CBC + ESR: EDTA; FBS: fluoride

        MvcResult samples = samplesOf(orderId, lab);
        assertThat(JsonPath.<java.util.List<?>>read(samples.getResponse().getContentAsString(), "$")).hasSize(2);
        mvc.perform(get("/api/lab-orders/" + orderId + "/samples").cookie(lab))
                .andExpect(jsonPath("$[?(@.requiredTubeType == 'EDTA')].tests[*].code").value(org.hamcrest.Matchers.containsInAnyOrder("CBC", "ESR")))
                .andExpect(jsonPath("$[0].sampleCode").value(matchesPattern("LAB-\\d{8}-\\d{4}")))
                .andExpect(jsonPath("$[0].status").value("ORDERED"))
                .andExpect(jsonPath("$[0].events", hasSize(1)))
                .andExpect(jsonPath("$[0].events[0].status").value("ORDERED"));
        assertThat(jdbc.queryForObject("SELECT count(DISTINCT sample_code) FROM samples", Long.class)).isEqualTo(2);
    }

    @Test
    void aTestOrderedLaterJoinsTheWaitingSampleForItsTube() throws Exception {
        String consultationId = consultation();
        String orderId = read(mvc.perform(json(post("/api/lab-orders"), "{\"consultationId\":\"" + consultationId
                + "\",\"testIds\":" + ids("CBC") + "}").cookie(doctor)).andReturn(), "$.id");

        mvc.perform(json(post("/api/lab-orders"), "{\"consultationId\":\"" + consultationId + "\",\"testIds\":"
                + ids("ESR", "TSH") + "}").cookie(doctor)).andExpect(status().isCreated());

        // ESR joined the EDTA sample; TSH (SST) got its own.
        assertThat(jdbc.queryForObject("SELECT count(*) FROM samples WHERE lab_order_id = ?", Long.class, UUID.fromString(orderId))).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM sample_items si JOIN samples s ON s.id = si.sample_id "
                + "WHERE s.lab_order_id = ? AND s.required_tube_type = 'EDTA'", Long.class, UUID.fromString(orderId))).isEqualTo(2);
    }

    // ---------------------------------------------------------------- collection

    @Test
    void collectingRecordsTheTubeSiteAndWho() throws Exception {
        consultation();
        String sampleId = sampleFor(order("ROUTINE", "CBC"), "EDTA");

        collect(lab, sampleId, "EDTA", "Left arm", false)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COLLECTED"))
                .andExpect(jsonPath("$.tubeTypeUsed").value("EDTA"))
                .andExpect(jsonPath("$.bodySite").value("Left arm"))
                .andExpect(jsonPath("$.tubeMismatch").value(false))
                .andExpect(jsonPath("$.events", hasSize(2)))
                .andExpect(jsonPath("$.events[1].status").value("COLLECTED"))
                .andExpect(jsonPath("$.events[1].actorName").value("Test LAB_TECHNICIAN"));
        collect(lab, sampleId, "EDTA", "Left arm", false)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NOT_WAITING_FOR_COLLECTION"));
    }

    @Test
    void bloodNeedsABodySiteButACupDoesNot() throws Exception {
        consultation();
        String orderId = order("ROUTINE", "CBC", "URINE");

        collect(lab, sampleFor(orderId, "EDTA"), "EDTA", null, false)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BODY_SITE_REQUIRED"));
        collect(lab, sampleFor(orderId, "URINE_CUP"), "URINE_CUP", null, false).andExpect(status().isOk());
    }

    @Test
    void aWrongTubeIsFlaggedAndNeedsConfirmation() throws Exception {
        consultation();
        String sampleId = sampleFor(order("ROUTINE", "CBC"), "EDTA");

        collect(lab, sampleId, "SST", "Left arm", false)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TUBE_MISMATCH"));
        assertThat(statusOf(sampleId)).isEqualTo("ORDERED");

        collect(lab, sampleId, "SST", "Left arm", true)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tubeMismatch").value(true))
                .andExpect(jsonPath("$.tubeTypeUsed").value("SST"))
                .andExpect(jsonPath("$.requiredTubeType").value("EDTA"));
        assertThat(jdbc.queryForObject("SELECT detail FROM sample_status_events WHERE sample_id = ? AND status = 'COLLECTED'",
                String.class, UUID.fromString(sampleId))).contains("TUBE MISMATCH");
    }

    // ---------------------------------------------------------------- receipt check

    @Test
    void acceptingAtTheReceiptCheckMovesTheSampleToTheLab() throws Exception {
        consultation();
        String sampleId = sampleFor(order("ROUTINE", "CBC"), "EDTA");

        receive(lab, sampleId, "{\"accepted\":true}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NOT_WAITING_FOR_RECEIPT"));
        collect(lab, sampleId, "EDTA", "Left arm", false);
        receive(lab, sampleId, "{\"accepted\":true}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RECEIVED_AT_LAB"))
                .andExpect(jsonPath("$.events", hasSize(3)))
                .andExpect(jsonPath("$.events[2].status").value("RECEIVED_AT_LAB"));
        receive(lab, sampleId, "{\"accepted\":true}").andExpect(status().isConflict());
    }

    @Test
    void rejectingKeepsTheSampleNotifiesTheFrontDeskAndCreatesTheRedraw() throws Exception {
        consultation();
        String orderId = order("ROUTINE", "CBC", "ESR");
        String sampleId = sampleFor(orderId, "EDTA");
        collect(lab, sampleId, "EDTA", "Left arm", false);

        MvcResult rejected = receive(lab, sampleId, "{\"accepted\":false,\"reason\":\"HEMOLYZED\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.rejection.reason").value("HEMOLYZED"))
                .andExpect(jsonPath("$.rejection.stage").value("RECEIVED_AT_LAB"))
                .andExpect(jsonPath("$.rejection.flaggedBy").value("Test LAB_TECHNICIAN"))
                .andExpect(jsonPath("$.redrawSampleCode").value(matchesPattern("LAB-\\d{8}-\\d{4}")))
                .andReturn();

        // The rejected sample is kept as it was; a clean redraw covers the same tests.
        String redrawId = read(rejected, "$.redrawSampleId");
        assertThat(statusOf(sampleId)).isEqualTo("REJECTED");
        assertThat(statusOf(redrawId)).isEqualTo("ORDERED");
        assertThat(jdbc.queryForObject("SELECT redraw_of_sample_id FROM samples WHERE id = ?", UUID.class,
                UUID.fromString(redrawId))).isEqualTo(UUID.fromString(sampleId));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM sample_items WHERE sample_id = ?", Long.class,
                UUID.fromString(redrawId))).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM sample_items WHERE sample_id = ?", Long.class,
                UUID.fromString(sampleId))).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT front_desk_notified_at IS NOT NULL FROM rejection_records WHERE sample_id = ?",
                Boolean.class, UUID.fromString(sampleId))).isTrue();

        // The redraw can be walked through like any sample.
        collect(lab, redrawId, "EDTA", "Right arm", false).andExpect(status().isOk());
        receive(lab, redrawId, "{\"accepted\":true}").andExpect(jsonPath("$.status").value("RECEIVED_AT_LAB"));
    }

    @Test
    void rejectionNeedsAFittingReasonAndANoteForOther() throws Exception {
        consultation();
        String sampleId = sampleFor(order("ROUTINE", "CBC"), "EDTA");
        collect(lab, sampleId, "EDTA", "Left arm", false);

        receive(lab, sampleId, "{\"accepted\":false}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("REASON_REQUIRED"));
        receive(lab, sampleId, "{\"accepted\":false,\"reason\":\"SAMPLE_EXHAUSTED\"}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("REASON_NOT_ALLOWED"));
        receive(lab, sampleId, "{\"accepted\":false,\"reason\":\"OTHER\"}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("NOTE_REQUIRED"));
        assertThat(statusOf(sampleId)).isEqualTo("COLLECTED");

        receive(lab, sampleId, "{\"accepted\":false,\"reason\":\"OTHER\",\"note\":\"Label unreadable\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.rejection.note").value("Label unreadable"));
    }

    // ---------------------------------------------------------------- the database backs the rules

    @Test
    void theDatabaseRefusesIllegalMovesEditsAndDeletes() throws Exception {
        consultation();
        String sampleId = sampleFor(order("ROUTINE", "CBC"), "EDTA");
        UUID id = UUID.fromString(sampleId);

        // Cannot skip ahead or jump to a later stage.
        assertThatThrownBy(() -> jdbc.update("UPDATE samples SET status = 'VERIFIED' WHERE id = ?", id))
                .isInstanceOf(DataAccessException.class);
        collect(lab, sampleId, "EDTA", "Left arm", false);
        receive(lab, sampleId, "{\"accepted\":false,\"reason\":\"CLOTTED\"}");

        // A rejected sample is final and permanent.
        assertThatThrownBy(() -> jdbc.update("UPDATE samples SET status = 'COLLECTED' WHERE id = ?", id))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("DELETE FROM samples WHERE id = ?", id)).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE sample_status_events SET detail = 'x'")).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("DELETE FROM sample_status_events")).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE rejection_records SET reason = 'OTHER'")).isInstanceOf(DataAccessException.class);
        // A reason must fit the stage it was raised at.
        assertThatThrownBy(() -> jdbc.update("INSERT INTO rejection_records (sample_id, rejected_at_stage, reason, flagged_by_user_id) "
                + "SELECT id, 'RECEIVED_AT_LAB', 'SAMPLE_EXHAUSTED', collected_by_user_id FROM samples WHERE id <> ? LIMIT 1", id))
                .isInstanceOf(DataAccessException.class);
    }

    // ---------------------------------------------------------------- removing tests from an order

    @Test
    void aTestCannotComeOffOnceItsSampleIsCollected() throws Exception {
        consultation();
        MvcResult created = mvc.perform(json(post("/api/lab-orders"), "{\"patientId\":\"" + patientId + "\",\"testIds\":"
                + ids("CBC", "FBS") + "}").cookie(doctor)).andExpect(status().isCreated()).andReturn();
        String orderId = read(created, "$.id");
        String cbcItem = jdbc.queryForObject("SELECT id FROM lab_order_items WHERE lab_order_id = ? AND test_name LIKE 'Complete%'",
                UUID.class, UUID.fromString(orderId)).toString();
        collect(lab, sampleFor(orderId, "EDTA"), "EDTA", "Left arm", false);

        mvc.perform(delete("/api/lab-orders/" + orderId + "/items/" + cbcItem).header(CsrfHeaderFilter.HEADER, "1").cookie(doctor))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SAMPLE_COLLECTED"));
        mvc.perform(json(post("/api/lab-orders/" + orderId + "/cancel"), "{}").cookie(doctor))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SAMPLE_COLLECTED"));
        assertThat(jdbc.queryForObject("SELECT status FROM lab_orders WHERE id = ?", String.class, UUID.fromString(orderId)))
                .isEqualTo("ORDERED");
    }

    @Test
    void removingEveryTestOfAWaitingSampleCancelsIt() throws Exception {
        consultation();
        MvcResult created = mvc.perform(json(post("/api/lab-orders"), "{\"patientId\":\"" + patientId + "\",\"testIds\":"
                + ids("CBC", "FBS") + "}").cookie(doctor)).andExpect(status().isCreated()).andReturn();
        String orderId = read(created, "$.id");
        String fbsItem = jdbc.queryForObject("SELECT id FROM lab_order_items WHERE lab_order_id = ? AND test_name LIKE 'Fasting%'",
                UUID.class, UUID.fromString(orderId)).toString();

        mvc.perform(delete("/api/lab-orders/" + orderId + "/items/" + fbsItem).header(CsrfHeaderFilter.HEADER, "1").cookie(doctor))
                .andExpect(status().isOk());
        assertThat(statusOf(sampleFor(orderId, "FLUORIDE"))).isEqualTo("CANCELLED");
        assertThat(statusOf(sampleFor(orderId, "EDTA"))).isEqualTo("ORDERED");

        mvc.perform(json(post("/api/lab-orders/" + orderId + "/cancel"), "{}").cookie(doctor)).andExpect(status().isOk());
        assertThat(statusOf(sampleFor(orderId, "EDTA"))).isEqualTo("CANCELLED");
        // Cancelled samples are still on record, with their history.
        assertThat(jdbc.queryForObject("SELECT count(*) FROM sample_status_events WHERE status = 'CANCELLED'", Long.class)).isEqualTo(2);
    }

    // ---------------------------------------------------------------- who sees what

    @Test
    void patientsSeeTheJourneyButNeitherStaffNamesNorRejectionDetail() throws Exception {
        consultation();
        String orderId = order("ROUTINE", "CBC");
        String sampleId = sampleFor(orderId, "EDTA");
        collect(lab, sampleId, "EDTA", "Left arm", false);
        receive(lab, sampleId, "{\"accepted\":false,\"reason\":\"HEMOLYZED\",\"note\":\"Visibly lysed\"}");

        mvc.perform(get("/api/samples/mine").cookie(patient))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
        mvc.perform(get("/api/samples/" + sampleId + "/status").cookie(patient))
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.rejection").doesNotExist())
                .andExpect(jsonPath("$.events[0].actorName").doesNotExist())
                .andExpect(jsonPath("$.events[1].detail").doesNotExist())
                .andExpect(jsonPath("$.redrawSampleCode").exists());
        mvc.perform(get("/api/lab-orders/" + orderId + "/samples").cookie(patient)).andExpect(jsonPath("$", hasSize(2)));
        mvc.perform(get("/api/samples/" + sampleId + "/status").cookie(otherPatient)).andExpect(status().isNotFound());
        mvc.perform(get("/api/lab-orders/" + orderId + "/samples").cookie(otherPatient)).andExpect(status().isNotFound());
        mvc.perform(get("/api/samples/" + sampleId + "/events").cookie(patient)).andExpect(status().isForbidden());
    }

    @Test
    void staffSeeTheFullCustodyLog() throws Exception {
        consultation();
        String sampleId = sampleFor(order("ROUTINE", "CBC"), "EDTA");
        collect(lab, sampleId, "EDTA", "Left arm", false);

        mvc.perform(get("/api/samples/" + sampleId + "/events").cookie(lab))
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].actorName").value("Test DOCTOR"))
                .andExpect(jsonPath("$[1].actorName").value("Test LAB_TECHNICIAN"))
                .andExpect(jsonPath("$[1].detail").value("Left arm · EDTA"));
        mvc.perform(get("/api/samples/" + sampleId + "/events").cookie(admin)).andExpect(status().isOk());
        mvc.perform(get("/api/samples/" + sampleId + "/events").cookie(doctor)).andExpect(status().isForbidden());
        mvc.perform(get("/api/samples/" + sampleId + "/status").cookie(doctor))
                .andExpect(status().isOk()).andExpect(jsonPath("$.events[1].actorName").doesNotExist());
        mvc.perform(get("/api/samples/" + sampleId + "/status").cookie(reception)).andExpect(status().isOk());
    }

    @Test
    void onlyTheLabTechnicianCollectsAndReceives() throws Exception {
        consultation();
        String sampleId = sampleFor(order("ROUTINE", "CBC"), "EDTA");

        for (Cookie who : new Cookie[] {doctor, patient, reception, admin}) {
            collect(who, sampleId, "EDTA", "Left arm", false).andExpect(status().isForbidden());
            receive(who, sampleId, "{\"accepted\":true}").andExpect(status().isForbidden());
        }
        assertThat(statusOf(sampleId)).isEqualTo("ORDERED");
    }

    // ---------------------------------------------------------------- lab lists and lookup

    @Test
    void theLabQueueListsUrgentThenRedrawsThenOldest() throws Exception {
        consultation();
        String routine = order("ROUTINE", "CBC");
        String urgent = order("URGENT", "TSH");
        String redraw = order("ROUTINE", "FBS");
        String fluoride = sampleFor(redraw, "FLUORIDE");
        collect(lab, fluoride, "FLUORIDE", "Left arm", false);
        receive(lab, fluoride, "{\"accepted\":false,\"reason\":\"CLOTTED\"}");

        mvc.perform(get("/api/samples").param("status", "ORDERED").cookie(lab))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content[0].labOrderId").value(urgent))
                .andExpect(jsonPath("$.content[1].labOrderId").value(redraw))
                .andExpect(jsonPath("$.content[1].redraw").value(true))
                .andExpect(jsonPath("$.content[2].labOrderId").value(routine))
                .andExpect(jsonPath("$.content[2].testNames[0]").value("Complete Blood Count"));
        collect(lab, sampleFor(routine, "EDTA"), "EDTA", "Left arm", false);
        mvc.perform(get("/api/samples").param("status", "COLLECTED").cookie(lab))
                .andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/api/samples").param("status", "VERIFIED").cookie(lab)).andExpect(status().isBadRequest());
        mvc.perform(get("/api/samples").cookie(patient)).andExpect(status().isForbidden());
    }

    @Test
    void aSampleIsFoundByTheCodeOnItsLabel() throws Exception {
        consultation();
        String sampleId = sampleFor(order("ROUTINE", "CBC"), "EDTA");
        String code = jdbc.queryForObject("SELECT sample_code FROM samples WHERE id = ?", String.class, UUID.fromString(sampleId));

        mvc.perform(get("/api/samples/by-code/" + code.toLowerCase()).cookie(lab))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(sampleId))
                .andExpect(jsonPath("$.patient.fullName").value("Test PATIENT"))
                .andExpect(jsonPath("$.orderCode").value(matchesPattern("LO-\\d{6}")));
        mvc.perform(get("/api/samples/by-code/LAB-19990101-0001").cookie(lab)).andExpect(status().isNotFound());
        mvc.perform(get("/api/samples/by-code/" + code).cookie(patient)).andExpect(status().isForbidden());
    }

    // ---------------------------------------------------------------- front-desk notifications

    @Test
    void theFrontDeskGetsARedrawAlertAndMarksItHandled() throws Exception {
        consultation();
        String sampleId = sampleFor(order("ROUTINE", "CBC"), "EDTA");
        collect(lab, sampleId, "EDTA", "Left arm", false);
        receive(lab, sampleId, "{\"accepted\":false,\"reason\":\"INSUFFICIENT_VOLUME\"}");

        MvcResult inbox = mvc.perform(get("/api/notifications").cookie(reception))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.open").value(1))
                .andExpect(jsonPath("$.items[0].type").value("SAMPLE_REJECTED"))
                .andExpect(jsonPath("$.items[0].title").value("Redraw needed — Test PATIENT"))
                .andExpect(jsonPath("$.items[0].sampleId").value(sampleId))
                .andReturn();
        assertThat(read(inbox, "$.items[0].message")).contains("not enough volume").contains("+911234567890");

        mvc.perform(get("/api/dashboard/receptionist").cookie(reception))
                .andExpect(jsonPath("$.widgets[1].type").value("sampleAlerts"))
                .andExpect(jsonPath("$.widgets[1].data.open").value(1));

        String id = read(inbox, "$.items[0].id");
        mvc.perform(json(post("/api/notifications/" + id + "/handle"), "{}").cookie(lab)).andExpect(status().isForbidden());
        mvc.perform(json(post("/api/notifications/" + id + "/handle"), "{}").cookie(reception))
                .andExpect(status().isOk()).andExpect(jsonPath("$.open").value(0));
        mvc.perform(get("/api/notifications").cookie(reception)).andExpect(jsonPath("$.items", hasSize(0)));
        mvc.perform(get("/api/notifications").cookie(doctor)).andExpect(status().isForbidden());
    }

    @Test
    void theLabDashboardShowsTheSampleBench() throws Exception {
        consultation();
        String orderId = order("URGENT", "CBC", "FBS");
        collect(lab, sampleFor(orderId, "EDTA"), "EDTA", "Left arm", false);

        mvc.perform(get("/api/dashboard/lab-technician").cookie(lab))
                .andExpect(jsonPath("$.widgets[0].data[0].value").value(1))
                .andExpect(jsonPath("$.widgets[0].data[1].value").value(1))
                .andExpect(jsonPath("$.widgets[1].data.toCollect", hasSize(1)))
                .andExpect(jsonPath("$.widgets[1].data.toReceive", hasSize(1)))
                .andExpect(jsonPath("$.widgets[2].data.FLUORIDE").value(1));
    }
}
