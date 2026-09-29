package com.cdlms.result;

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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Phase 08: results, range flags, the verification gate, retests, rejection in testing, reports (ADR-025). */
class ResultFlowTest extends IntegrationTest {

    @org.springframework.beans.factory.annotation.Autowired
    private ReportRepository reportRepository;

    private Cookie reception;
    private Cookie doctor;
    private Cookie patient;
    private Cookie otherPatient;
    private Cookie lab;
    private Cookie pathologist;
    private Cookie admin;
    private UUID doctorId;
    private UUID patientId;
    private UUID pathologistUserId;

    @BeforeEach
    void setUp() {
        reception = testUsers.loginCookie(testUsers.create(Role.RECEPTIONIST));
        User doctorUser = testUsers.create(Role.DOCTOR);
        doctor = testUsers.loginCookie(doctorUser);
        User patientUser = testUsers.create(Role.PATIENT);
        patient = testUsers.loginCookie(patientUser);
        otherPatient = testUsers.loginCookie(testUsers.create(Role.PATIENT, "other.patient@test.local"));
        lab = testUsers.loginCookie(testUsers.create(Role.LAB_TECHNICIAN));
        User pathologistUser = testUsers.create(Role.PATHOLOGIST);
        pathologist = testUsers.loginCookie(pathologistUser);
        admin = testUsers.loginCookie(testUsers.create(Role.ADMIN));

        pathologistUserId = pathologistUser.getId();
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

    private String testId(String code) {
        return jdbc.queryForObject("SELECT id FROM lab_tests WHERE code = ?", UUID.class, code).toString();
    }

    /** Gives the doctor a care relationship (checked-in patient, open consultation). */
    private void consultation() throws Exception {
        String appointmentId = read(mvc.perform(json(post("/api/queue/tokens"),
                        "{\"patientId\":\"" + patientId + "\",\"doctorId\":\"" + doctorId + "\"}").cookie(reception))
                .andExpect(status().isCreated()).andReturn(), "$.id");
        mvc.perform(json(post("/api/consultations"), "{\"appointmentId\":\"" + appointmentId + "\"}").cookie(doctor))
                .andExpect(status().isOk());
    }

    private String orderId;

    /** Orders one test directly, then collects and receives its sample; returns the sample id, ready for testing. */
    private String receivedSample(String testCode, String tube) throws Exception {
        orderId = read(mvc.perform(json(post("/api/lab-orders"), "{\"patientId\":\"" + patientId + "\",\"testIds\":[\""
                + testId(testCode) + "\"]}").cookie(doctor)).andExpect(status().isCreated()).andReturn(), "$.id");
        String sampleId = jdbc.queryForObject("SELECT id FROM samples WHERE lab_order_id = ? AND required_tube_type = ? "
                + "ORDER BY created_at DESC LIMIT 1", UUID.class, UUID.fromString(orderId), tube).toString();
        mvc.perform(json(post("/api/samples/" + sampleId + "/collect"), "{\"tubeTypeUsed\":\"" + tube
                + "\",\"bodySite\":\"Left arm\"}").cookie(lab)).andExpect(status().isOk());
        mvc.perform(json(post("/api/samples/" + sampleId + "/receive"), "{\"accepted\":true}").cookie(lab))
                .andExpect(status().isOk());
        return sampleId;
    }

    private String inTesting(String testCode, String tube) throws Exception {
        String sampleId = receivedSample(testCode, tube);
        mvc.perform(post("/api/samples/" + sampleId + "/start-testing").header(CsrfHeaderFilter.HEADER, "1").cookie(lab))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("IN_TESTING"));
        return sampleId;
    }

    /** The values to enter, by parameter name; anything not in the map gets "1". Read from the sample's entry sheet. */
    private String valuesBody(String sampleId, Map<String, String> byName) throws Exception {
        MvcResult sheet = mvc.perform(get("/api/samples/" + sampleId + "/results").cookie(lab)).andExpect(status().isOk()).andReturn();
        List<Map<String, Object>> parameters = JsonPath.read(sheet.getResponse().getContentAsString(), "$.sheet[*].parameters[*]");
        StringBuilder out = new StringBuilder("{\"analyzer\":\"Sysmex XN-1000\",\"values\":[");
        for (int i = 0; i < parameters.size(); i++) {
            Map<String, Object> p = parameters.get(i);
            String fallback = "TEXT".equals(p.get("valueType")) ? "Negative" : "1";
            out.append(i == 0 ? "" : ",").append("{\"parameterId\":\"").append(p.get("parameterId")).append("\",\"value\":\"")
                    .append(byName.getOrDefault((String) p.get("name"), fallback)).append("\"}");
        }
        return out.append("]}").toString();
    }

    private ResultActions enter(Cookie who, String sampleId, String body) throws Exception {
        return mvc.perform(json(post("/api/samples/" + sampleId + "/results"), body).cookie(who));
    }

    /** CBC values: Hb 13.2 (normal), WBC 12.5 (high), platelets 250, RBC 4.8, haematocrit 40 — unless overridden. */
    private Map<String, String> cbc(String... overrides) {
        Map<String, String> v = new HashMap<>(Map.of("Haemoglobin", "13.2", "Total WBC count", "12.5", "Platelet count", "250",
                "RBC count", "4.8", "Haematocrit", "40"));
        for (int i = 0; i < overrides.length; i += 2) {
            v.put(overrides[i], overrides[i + 1]);
        }
        return v;
    }

    private String enteredSample() throws Exception {
        String sampleId = inTesting("CBC", "EDTA");
        enter(lab, sampleId, valuesBody(sampleId, cbc())).andExpect(status().isOk());
        return sampleId;
    }

    private ResultActions verify(Cookie who, String sampleId) throws Exception {
        return mvc.perform(post("/api/samples/" + sampleId + "/verify").header(CsrfHeaderFilter.HEADER, "1").cookie(who));
    }

    private String statusOf(String sampleId) {
        return jdbc.queryForObject("SELECT status FROM samples WHERE id = ?", String.class, UUID.fromString(sampleId));
    }

    // ---------------------------------------------------------------- testing and result entry

    @Test
    void testingStartsOnlyOnAnAcceptedSample() throws Exception {
        consultation();
        String sampleId = receivedSample("CBC", "EDTA");

        mvc.perform(post("/api/samples/" + sampleId + "/start-testing").header(CsrfHeaderFilter.HEADER, "1").cookie(pathologist))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/samples/" + sampleId + "/start-testing").header(CsrfHeaderFilter.HEADER, "1").cookie(lab))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_TESTING"))
                .andExpect(jsonPath("$.sheet[0].testCode").value("CBC"))
                .andExpect(jsonPath("$.sheet[0].parameters", hasSize(5)));
        mvc.perform(post("/api/samples/" + sampleId + "/start-testing").header(CsrfHeaderFilter.HEADER, "1").cookie(lab))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("NOT_READY_FOR_TESTING"));
    }

    @Test
    void enteringResultsFlagsEachNumberAgainstItsRange() throws Exception {
        consultation();
        String sampleId = inTesting("CBC", "EDTA");

        // Hb 6.5 is at or below the critical limit (7.0); WBC 12.5 is above the normal range.
        enter(lab, sampleId, valuesBody(sampleId, cbc("Haemoglobin", "6.5")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESULT_ENTERED"))
                .andExpect(jsonPath("$.attempts", hasSize(1)))
                .andExpect(jsonPath("$.attempts[0].attemptNumber").value(1))
                .andExpect(jsonPath("$.attempts[0].status").value("PENDING_VERIFICATION"))
                .andExpect(jsonPath("$.attempts[0].analyzer").value("Sysmex XN-1000"))
                .andExpect(jsonPath("$.attempts[0].enteredBy").value("Test LAB_TECHNICIAN"))
                .andExpect(jsonPath("$.attempts[0].critical").value(true))
                .andExpect(jsonPath("$.attempts[0].values[?(@.name == 'Haemoglobin')].flag").value("CRITICAL_LOW"))
                .andExpect(jsonPath("$.attempts[0].values[?(@.name == 'Total WBC count')].flag").value("HIGH"))
                .andExpect(jsonPath("$.attempts[0].values[?(@.name == 'Platelet count')].flag").value("NORMAL"))
                // The range it was flagged against is kept on the value.
                .andExpect(jsonPath("$.attempts[0].values[?(@.name == 'Haemoglobin')].refLow").value(12.0))
                .andExpect(jsonPath("$.attempts[0].values[?(@.name == 'Haemoglobin')].criticalLow").value(7.0));
        assertThat(statusOf(sampleId)).isEqualTo("RESULT_ENTERED");
        assertThat(jdbc.queryForObject("SELECT detail FROM sample_status_events WHERE sample_id = ? AND status = 'RESULT_ENTERED'",
                String.class, UUID.fromString(sampleId))).contains("CRITICAL VALUE").contains("Sysmex");
    }

    @Test
    void textParametersAreRecordedAsTypedWithoutAFlag() throws Exception {
        consultation();
        String sampleId = inTesting("NS1", "SST");

        enter(lab, sampleId, valuesBody(sampleId, Map.of("Dengue NS1 antigen", "Positive")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.attempts[0].values[0].valueType").value("TEXT"))
                .andExpect(jsonPath("$.attempts[0].values[0].textValue").value("Positive"))
                .andExpect(jsonPath("$.attempts[0].values[0].flag").doesNotExist())
                .andExpect(jsonPath("$.attempts[0].critical").value(false));
    }

    @Test
    void everyParameterNeedsAValidValue() throws Exception {
        consultation();
        String sampleId = inTesting("CBC", "EDTA");
        String full = valuesBody(sampleId, cbc());
        List<Map<String, Object>> values = JsonPath.read(full, "$.values");
        String firstId = (String) values.get(0).get("parameterId");

        // One value missing.
        String missing = "{\"values\":[" + full.substring(full.indexOf("[{") + 1, full.indexOf("},{") + 1) + "]}";
        enter(lab, sampleId, missing).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("MISSING_VALUES"));
        // Not a number.
        enter(lab, sampleId, full.replace("\"13.2\"", "\"lots\"")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_VALUE"));
        // Too many decimal places.
        enter(lab, sampleId, full.replace("\"13.2\"", "\"13.23456\"")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_VALUE"));
        // A parameter that isn't on this sample.
        enter(lab, sampleId, full.replaceFirst("\\{\"parameterId\":\"" + firstId + "\"",
                "{\"parameterId\":\"" + UUID.randomUUID() + "\"")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("UNKNOWN_PARAMETER"));
        // The same parameter twice.
        enter(lab, sampleId, full.replace("]}", ",{\"parameterId\":\"" + firstId + "\",\"value\":\"1\"}]}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("DUPLICATE_VALUE"));
        assertThat(statusOf(sampleId)).isEqualTo("IN_TESTING");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM sample_results", Long.class)).isZero();
    }

    @Test
    void resultsCanOnlyBeEnteredWhileTheSampleIsInTesting() throws Exception {
        consultation();
        String sampleId = receivedSample("CBC", "EDTA"); // accepted but testing hasn't started
        mvc.perform(get("/api/samples/" + sampleId + "/results").cookie(lab)).andExpect(status().isOk());
        String body = valuesBody(sampleId, cbc());

        enter(lab, sampleId, body).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("NOT_IN_TESTING"));
        for (Cookie who : new Cookie[] {doctor, patient, reception, pathologist, admin}) {
            enter(who, sampleId, body).andExpect(status().isForbidden());
        }
    }

    // ---------------------------------------------------------------- verification and the report gate

    @Test
    void verifyingSignsOffTheResultAndCreatesTheReport() throws Exception {
        consultation();
        String sampleId = enteredSample();

        mvc.perform(get("/api/samples/pending-verification").cookie(pathologist))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].sampleId").value(sampleId))
                .andExpect(jsonPath("$.content[0].abnormalCount").value(1));

        verify(pathologist, sampleId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REPORT_GENERATED"))
                .andExpect(jsonPath("$.attempts[0].status").value("VERIFIED"))
                .andExpect(jsonPath("$.attempts[0].verifiedBy").value("Test PATHOLOGIST"));

        assertThat(statusOf(sampleId)).isEqualTo("REPORT_GENERATED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM reports WHERE sample_id = ?", Long.class, UUID.fromString(sampleId))).isEqualTo(1);
        assertThat(jdbc.queryForList("SELECT status FROM sample_status_events WHERE sample_id = ? ORDER BY occurred_at, status",
                String.class, UUID.fromString(sampleId))).contains("VERIFIED", "REPORT_GENERATED");
        mvc.perform(get("/api/samples/pending-verification").cookie(pathologist)).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/samples/verified-by-me").cookie(pathologist))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].sampleCode").value(matchesPattern("LAB-\\d{8}-\\d{4}")));
        verify(pathologist, sampleId).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("NOT_AWAITING_VERIFICATION"));
    }

    @Test
    void onlyAPathologistCanVerifyOrReturn() throws Exception {
        consultation();
        String sampleId = enteredSample();

        for (Cookie who : new Cookie[] {lab, doctor, patient, reception, admin}) {
            verify(who, sampleId).andExpect(status().isForbidden());
            mvc.perform(json(post("/api/samples/" + sampleId + "/return-for-retest"), "{\"reason\":\"QC_CONCERN\"}").cookie(who))
                    .andExpect(status().isForbidden());
        }
        assertThat(statusOf(sampleId)).isEqualTo("RESULT_ENTERED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM reports", Long.class)).isZero();
    }

    @Test
    void theOneWhoEnteredTheResultCannotVerifyIt() throws Exception {
        consultation();
        String sampleId = inTesting("CBC", "EDTA");
        // A result entered by the pathologist's own account (not possible through the API, so made directly).
        UUID resultId = jdbc.queryForObject("INSERT INTO sample_results (sample_id, attempt_number, entered_by_user_id) "
                + "VALUES (?, 1, ?) RETURNING id", UUID.class, UUID.fromString(sampleId), pathologistUserId);
        assertThat(resultId).isNotNull();
        jdbc.update("UPDATE samples SET status = 'RESULT_ENTERED' WHERE id = ?", UUID.fromString(sampleId));

        verify(pathologist, sampleId).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CANNOT_VERIFY_OWN"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM reports", Long.class)).isZero();
    }

    @Test
    void theDatabaseRefusesAReportWithoutAVerifiedResult() throws Exception {
        consultation();
        String sampleId = enteredSample();
        UUID id = UUID.fromString(sampleId);
        UUID userId = pathologistUserId;

        // Waiting for verification is not enough.
        assertThatThrownBy(() -> jdbc.update("INSERT INTO reports (sample_id, generated_by_user_id) VALUES (?, ?)", id, userId))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("verified");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM reports", Long.class)).isZero();

        verify(pathologist, sampleId).andExpect(status().isOk());
        // Once verified there is exactly one, and it can't be deleted or edited.
        assertThat(jdbc.queryForObject("SELECT count(*) FROM reports", Long.class)).isEqualTo(1);
        assertThatThrownBy(() -> jdbc.update("DELETE FROM reports")).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE reports SET generated_at = now() - interval '1 day'")).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO reports (sample_id, generated_by_user_id) VALUES (?, ?)", id, userId))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void resultsAreProtectedFromRewriting() throws Exception {
        consultation();
        String sampleId = enteredSample();
        UUID id = UUID.fromString(sampleId);

        assertThatThrownBy(() -> jdbc.update("UPDATE result_values SET numeric_value = 99")).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("DELETE FROM result_values")).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("DELETE FROM sample_results")).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE sample_results SET analyzer = 'Other'")).isInstanceOf(DataAccessException.class);
        // A second live result for the same sample is refused.
        assertThatThrownBy(() -> jdbc.update("INSERT INTO sample_results (sample_id, attempt_number, entered_by_user_id) "
                + "SELECT sample_id, 2, entered_by_user_id FROM sample_results WHERE sample_id = ?", id))
                .isInstanceOf(DataAccessException.class);

        verify(pathologist, sampleId).andExpect(status().isOk());
        // A verified result is final.
        assertThatThrownBy(() -> jdbc.update("UPDATE sample_results SET status = 'RETURNED_FOR_RETEST'")).isInstanceOf(DataAccessException.class);
    }

    // ---------------------------------------------------------------- return for retest

    @Test
    void returningForRetestKeepsTheAttemptAndTheRetestIsTheNext() throws Exception {
        consultation();
        String sampleId = inTesting("CBC", "EDTA");
        enter(lab, sampleId, valuesBody(sampleId, cbc("Haemoglobin", "6.5"))).andExpect(status().isOk());

        mvc.perform(json(post("/api/samples/" + sampleId + "/return-for-retest"), "{}").cookie(pathologist))
                .andExpect(status().isBadRequest());
        mvc.perform(json(post("/api/samples/" + sampleId + "/return-for-retest"), "{\"reason\":\"OTHER\"}").cookie(pathologist))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("NOTE_REQUIRED"));
        mvc.perform(json(post("/api/samples/" + sampleId + "/return-for-retest"),
                        "{\"reason\":\"CRITICAL_VALUE_CONFIRMATION\",\"note\":\"Please rerun\"}").cookie(pathologist))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_TESTING"))
                .andExpect(jsonPath("$.retestCount").value(1))
                .andExpect(jsonPath("$.lastReturnReason").value("CRITICAL_VALUE_CONFIRMATION"))
                .andExpect(jsonPath("$.attempts[0].status").value("RETURNED_FOR_RETEST"))
                .andExpect(jsonPath("$.attempts[0].returnedBy").value("Test PATHOLOGIST"))
                .andExpect(jsonPath("$.attempts[0].returnNote").value("Please rerun"));

        // The bench sees it at the top, with the reason.
        mvc.perform(get("/api/samples/to-test").cookie(lab))
                .andExpect(jsonPath("$.content[0].id").value(sampleId))
                .andExpect(jsonPath("$.content[0].retestCount").value(1))
                .andExpect(jsonPath("$.content[0].returnReason").value("CRITICAL_VALUE_CONFIRMATION"));

        // The retest is attempt 2; the first stays as history.
        enter(lab, sampleId, valuesBody(sampleId, cbc("Haemoglobin", "13.0")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.attempts", hasSize(2)))
                .andExpect(jsonPath("$.attempts[1].attemptNumber").value(2))
                .andExpect(jsonPath("$.attempts[1].status").value("PENDING_VERIFICATION"))
                .andExpect(jsonPath("$.attempts[0].values[?(@.name == 'Haemoglobin')].numericValue").value(6.5));

        verify(pathologist, sampleId).andExpect(status().isOk());
        MvcResult report = mvc.perform(get("/api/reports/" + sampleId).cookie(doctor)).andExpect(status().isOk())
                .andExpect(jsonPath("$.attemptNumber").value(2)).andReturn();
        assertThat(read(report, "$.tests[0].values[?(@.name == 'Haemoglobin')].numericValue")).contains("13");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM sample_results WHERE status = 'RETURNED_FOR_RETEST'", Long.class)).isEqualTo(1);
    }

    // ---------------------------------------------------------------- rejection during testing

    @Test
    void aTechnicianCanRejectASampleInTestingWhichKeepsItsResultsAndCreatesTheRedraw() throws Exception {
        consultation();
        String sampleId = inTesting("CBC", "EDTA");
        enter(lab, sampleId, valuesBody(sampleId, cbc())).andExpect(status().isOk());
        mvc.perform(json(post("/api/samples/" + sampleId + "/return-for-retest"), "{\"reason\":\"QC_CONCERN\"}").cookie(pathologist))
                .andExpect(status().isOk());

        mvc.perform(json(post("/api/samples/" + sampleId + "/reject"), "{\"reason\":\"HEMOLYZED\"}").cookie(lab))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("REASON_NOT_ALLOWED"));
        mvc.perform(json(post("/api/samples/" + sampleId + "/reject"), "{\"reason\":\"SAMPLE_EXHAUSTED\"}").cookie(pathologist))
                .andExpect(status().isForbidden());
        MvcResult rejected = mvc.perform(json(post("/api/samples/" + sampleId + "/reject"), "{\"reason\":\"SAMPLE_EXHAUSTED\"}").cookie(lab))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.attempts", hasSize(1)))
                .andReturn();

        assertThat(statusOf(sampleId)).isEqualTo("REJECTED");
        assertThat(jdbc.queryForObject("SELECT rejected_at_stage FROM rejection_records WHERE sample_id = ?", String.class,
                UUID.fromString(sampleId))).isEqualTo("IN_TESTING");
        // The returned attempt is kept; the redraw is a clean sample starting again.
        assertThat(jdbc.queryForObject("SELECT count(*) FROM sample_results WHERE sample_id = ?", Long.class, UUID.fromString(sampleId))).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM samples WHERE redraw_of_sample_id = ? AND status = 'ORDERED'", Long.class,
                UUID.fromString(sampleId))).isEqualTo(1);
        mvc.perform(get("/api/notifications").cookie(reception)).andExpect(jsonPath("$.open").value(1));
        mvc.perform(json(post("/api/samples/" + sampleId + "/reject"), "{\"reason\":\"SAMPLE_EXHAUSTED\"}").cookie(lab))
                .andExpect(status().isConflict());
        assertThat(rejected.getResponse().getContentAsString()).contains("\"status\":\"REJECTED\"");
    }

    // ---------------------------------------------------------------- reports and dispatch

    @Test
    void theReportIsAvailableToDoctorsAtOnceAndToPatientsOnceDispatched() throws Exception {
        consultation();
        String sampleId = enteredSample();
        verify(pathologist, sampleId).andExpect(status().isOk());

        mvc.perform(get("/api/reports/" + sampleId).cookie(doctor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tests[0].testCode").value("CBC"))
                .andExpect(jsonPath("$.tests[0].values", hasSize(5)))
                .andExpect(jsonPath("$.verifier.name").value("Test PATHOLOGIST"))
                .andExpect(jsonPath("$.orderingDoctor").value("Test DOCTOR"))
                .andExpect(jsonPath("$.dispatchedAt").doesNotExist());
        // The patient can't see it yet, and neither can anyone else's patient.
        mvc.perform(get("/api/reports/" + sampleId).cookie(patient)).andExpect(status().isNotFound());
        mvc.perform(get("/api/reports/mine").cookie(patient)).andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(get("/api/reports/to-dispatch").cookie(lab))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].abnormal").value(true));
        mvc.perform(get("/api/reports/ordered-by-me").cookie(doctor)).andExpect(jsonPath("$.totalElements").value(1));

        mvc.perform(json(post("/api/reports/" + sampleId + "/dispatch"), "{\"channel\":\"DOWNLOAD_LINK\"}").cookie(reception))
                .andExpect(status().isForbidden());
        mvc.perform(json(post("/api/reports/" + sampleId + "/dispatch"), "{\"channel\":\"SMS\"}").cookie(lab))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("CHANNEL_UNAVAILABLE"));
        mvc.perform(json(post("/api/reports/" + sampleId + "/dispatch"), "{\"channel\":\"EMAIL\"}").cookie(lab))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dispatchedChannel").value("EMAIL"))
                .andExpect(jsonPath("$.dispatchedAt").exists())
                .andExpect(jsonPath("$.receiptConfirmedAt").doesNotExist());
        assertThat(statusOf(sampleId)).isEqualTo("DISPATCHED");
        mvc.perform(json(post("/api/reports/" + sampleId + "/dispatch"), "{\"channel\":\"DOWNLOAD_LINK\"}").cookie(lab))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ALREADY_DISPATCHED"));

        // Now the patient sees it; the first open is recorded as receipt, later ones don't change it.
        mvc.perform(get("/api/reports/mine").cookie(patient)).andExpect(jsonPath("$", hasSize(1)));
        MvcResult first = mvc.perform(get("/api/reports/" + sampleId).cookie(patient)).andExpect(status().isOk()).andReturn();
        String receipt = jdbc.queryForObject("SELECT receipt_confirmed_at::text FROM reports", String.class);
        assertThat(receipt).isNotNull();
        mvc.perform(get("/api/reports/" + sampleId).cookie(patient)).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT receipt_confirmed_at::text FROM reports", String.class)).isEqualTo(receipt);
        assertThat(first.getResponse().getContentAsString()).doesNotContain("\"returnNote\"");
        mvc.perform(get("/api/reports/" + sampleId).cookie(otherPatient)).andExpect(status().isNotFound());
        mvc.perform(get("/api/reports/" + sampleId).cookie(reception)).andExpect(status().isForbidden());
    }

    @Test
    void aDoctorOpeningAReportIsLogged() throws Exception {
        consultation();
        String sampleId = enteredSample();
        verify(pathologist, sampleId).andExpect(status().isOk());

        mvc.perform(get("/api/reports/" + sampleId).cookie(doctor)).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM patient_access_log WHERE resource = 'LAB_REPORT'", Long.class)).isEqualTo(1);
    }

    @Test
    void theReportPdfIsGeneratedBehindTheSameChecks() throws Exception {
        consultation();
        String sampleId = enteredSample();
        verify(pathologist, sampleId).andExpect(status().isOk());

        mvc.perform(get("/api/reports/" + sampleId + "/pdf").cookie(patient)).andExpect(status().isNotFound());
        MvcResult pdf = mvc.perform(get("/api/reports/" + sampleId + "/pdf").param("download", "true").cookie(doctor))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andReturn();
        assertThat(new String(pdf.getResponse().getContentAsByteArray(), 0, 5)).isEqualTo("%PDF-");
        assertThat(pdf.getResponse().getHeader("Content-Disposition")).contains("attachment").contains("LAB-");
        assertThat(pdf.getResponse().getHeader("Cache-Control")).contains("no-store");

        mvc.perform(json(post("/api/reports/" + sampleId + "/dispatch"), "{\"channel\":\"DOWNLOAD_LINK\"}").cookie(lab))
                .andExpect(status().isOk());
        mvc.perform(get("/api/reports/" + sampleId + "/pdf").cookie(patient)).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT receipt_confirmed_at IS NOT NULL FROM reports", Boolean.class)).isTrue();
    }

    // ---------------------------------------------------------------- the pathologist's view

    @Test
    void theQueueListsCriticalResultsFirst() throws Exception {
        consultation();
        String normal = enteredSample();
        String critical = inTesting("CBC", "EDTA");
        enter(lab, critical, valuesBody(critical, cbc("Haemoglobin", "6.0"))).andExpect(status().isOk());

        mvc.perform(get("/api/samples/pending-verification").cookie(pathologist))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].sampleId").value(critical))
                .andExpect(jsonPath("$.content[0].critical").value(true))
                .andExpect(jsonPath("$.content[1].sampleId").value(normal));
        mvc.perform(get("/api/samples/pending-verification").cookie(lab)).andExpect(status().isForbidden());
    }

    @Test
    void thePathologistSeesThePatientsEarlierValuesAsATrend() throws Exception {
        consultation();
        String first = inTesting("CBC", "EDTA");
        enter(lab, first, valuesBody(first, cbc("Haemoglobin", "11.0"))).andExpect(status().isOk());
        verify(pathologist, first).andExpect(status().isOk());

        String second = inTesting("CBC", "EDTA");
        enter(lab, second, valuesBody(second, cbc("Haemoglobin", "12.5"))).andExpect(status().isOk());

        mvc.perform(get("/api/samples/" + second + "/results").cookie(pathologist))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.patient.fullName").value("Test PATIENT"))
                .andExpect(jsonPath("$.trend.*[0].value").exists());
        // The technician gets no trend; the access is logged for the pathologist.
        mvc.perform(get("/api/samples/" + second + "/results").cookie(lab)).andExpect(jsonPath("$.trend").doesNotExist());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM patient_access_log WHERE user_id = ? AND resource = 'LAB_REPORT'",
                Long.class, pathologistUserId)).isEqualTo(1);
    }

    @Test
    void theTestingListPinsReturnedSamplesThenUrgentThenOldest() throws Exception {
        consultation();
        String waiting = receivedSample("ESR", "EDTA");
        String returned = inTesting("CBC", "EDTA");
        enter(lab, returned, valuesBody(returned, cbc())).andExpect(status().isOk());
        mvc.perform(json(post("/api/samples/" + returned + "/return-for-retest"), "{\"reason\":\"IMPLAUSIBLE_VALUE\"}").cookie(pathologist))
                .andExpect(status().isOk());

        mvc.perform(get("/api/samples/to-test").cookie(lab))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].id").value(returned))
                .andExpect(jsonPath("$.content[0].returnReason").value("IMPLAUSIBLE_VALUE"))
                .andExpect(jsonPath("$.content[1].id").value(waiting))
                .andExpect(jsonPath("$.content[1].status").value("RECEIVED_AT_LAB"));
        mvc.perform(get("/api/samples/to-test").cookie(pathologist)).andExpect(status().isForbidden());
    }

    // ---------------------------------------------------------------- dashboards

    @Test
    void thePathologistDashboardShowsTheQueueAndTheDay() throws Exception {
        consultation();
        String critical = inTesting("CBC", "EDTA");
        enter(lab, critical, valuesBody(critical, cbc("Haemoglobin", "6.0"))).andExpect(status().isOk());
        String other = enteredSample();
        verify(pathologist, other).andExpect(status().isOk());

        mvc.perform(get("/api/dashboard/pathologist").cookie(pathologist))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.widgets[0].data[0].value").value(1))
                .andExpect(jsonPath("$.widgets[0].data[1].value").value(1))
                .andExpect(jsonPath("$.widgets[0].data[2].value").value(1))
                .andExpect(jsonPath("$.widgets[1].type").value("verificationQueue"))
                .andExpect(jsonPath("$.widgets[1].data.results", hasSize(1)))
                .andExpect(jsonPath("$.widgets[1].data.results[0].sampleId").value(critical))
                .andExpect(jsonPath("$.widgets[1].data.total").value(1));
    }

    @Test
    void theLabDashboardShowsWhatToTestAndWhichReportsToSend() throws Exception {
        consultation();
        String returned = enteredSample();
        mvc.perform(json(post("/api/samples/" + returned + "/return-for-retest"), "{\"reason\":\"QC_CONCERN\"}").cookie(pathologist))
                .andExpect(status().isOk());
        String verified = enteredSample();
        verify(pathologist, verified).andExpect(status().isOk());

        mvc.perform(get("/api/dashboard/lab-technician").cookie(lab))
                .andExpect(jsonPath("$.widgets[0].data[2].value").value(1))
                .andExpect(jsonPath("$.widgets[0].data[2].hint").value("1 returned for retest"))
                .andExpect(jsonPath("$.widgets[0].data[3].value").value(1))
                .andExpect(jsonPath("$.widgets[?(@.type == 'testQueue')].data.samples[0].id").value(returned))
                .andExpect(jsonPath("$.widgets[?(@.type == 'dispatchQueue')].data.total").value(1));
    }

    @Test
    void patientsAndDoctorsSeeReportsOnTheirDashboards() throws Exception {
        consultation();
        String sampleId = enteredSample();
        verify(pathologist, sampleId).andExpect(status().isOk());

        // Verified but not dispatched: the doctor knows, the patient doesn't yet.
        mvc.perform(get("/api/dashboard/doctor").cookie(doctor))
                .andExpect(jsonPath("$.widgets[?(@.type == 'reportsReady')].data[0].sampleId").value(sampleId));
        mvc.perform(get("/api/dashboard/patient").cookie(patient))
                .andExpect(jsonPath("$.widgets[?(@.type == 'myReports')]", hasSize(0)));

        mvc.perform(json(post("/api/reports/" + sampleId + "/dispatch"), "{\"channel\":\"DOWNLOAD_LINK\"}").cookie(lab))
                .andExpect(status().isOk());
        mvc.perform(get("/api/dashboard/patient").cookie(patient))
                .andExpect(jsonPath("$.widgets[?(@.type == 'myReports')].data[0].sampleId").value(sampleId))
                .andExpect(jsonPath("$.widgets[?(@.type == 'myReports')].data[0].abnormal").value(true));
    }

    @Test
    void openingAReportAtTheSameTimeRecordsReceiptOnceWithoutErrors() throws Exception {
        consultation();
        String sampleId = enteredSample();
        verify(pathologist, sampleId).andExpect(status().isOk());
        mvc.perform(json(post("/api/reports/" + sampleId + "/dispatch"), "{\"channel\":\"DOWNLOAD_LINK\"}").cookie(lab))
                .andExpect(status().isOk());

        // Two tabs (or devices) opening the same report together used to make one of them fail.
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(8);
        java.util.concurrent.CountDownLatch go = new java.util.concurrent.CountDownLatch(1);
        List<java.util.concurrent.Future<Integer>> calls = new java.util.ArrayList<>();
        for (int i = 0; i < 8; i++) {
            calls.add(pool.submit(() -> {
                go.await();
                return mvc.perform(get("/api/reports/" + sampleId).cookie(patient)).andReturn().getResponse().getStatus();
            }));
        }
        go.countDown();
        for (java.util.concurrent.Future<Integer> call : calls) {
            assertThat(call.get()).isEqualTo(200);
        }
        pool.shutdown();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM reports WHERE receipt_confirmed_at IS NOT NULL", Long.class)).isEqualTo(1);
    }

    @Test
    void confirmingReceiptIsAtomicAndOnlyTheFirstCallCounts() throws Exception {
        consultation();
        String sampleId = enteredSample();
        verify(pathologist, sampleId).andExpect(status().isOk());
        UUID id = UUID.fromString(sampleId);

        // Nothing to confirm until the report has been dispatched.
        assertThat(reportRepository.confirmReceipt(id, java.time.Instant.now())).isZero();
        mvc.perform(json(post("/api/reports/" + sampleId + "/dispatch"), "{\"channel\":\"DOWNLOAD_LINK\"}").cookie(lab))
                .andExpect(status().isOk());

        assertThat(reportRepository.confirmReceipt(id, java.time.Instant.now())).isEqualTo(1);
        String first = jdbc.queryForObject("SELECT receipt_confirmed_at::text FROM reports", String.class);
        assertThat(reportRepository.confirmReceipt(id, java.time.Instant.now().plusSeconds(60))).isZero();
        assertThat(jdbc.queryForObject("SELECT receipt_confirmed_at::text FROM reports", String.class)).isEqualTo(first);
    }
}
