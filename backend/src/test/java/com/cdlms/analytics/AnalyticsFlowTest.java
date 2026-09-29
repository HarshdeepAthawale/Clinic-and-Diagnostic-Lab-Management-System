package com.cdlms.analytics;

import com.cdlms.auth.CsrfHeaderFilter;
import com.cdlms.support.IntegrationTest;
import com.cdlms.user.Role;
import com.cdlms.user.User;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Phase 09: the admin's insights are computed from real records and only the admin can see them (ADR-026). */
class AnalyticsFlowTest extends IntegrationTest {

    private Cookie admin;
    private Cookie lab;
    private Cookie doctor;
    private Cookie reception;
    private Cookie pathologist;
    private Cookie patient;
    private UUID doctorId;
    private UUID patientId;
    private UUID labUserId;
    private UUID pathologistUserId;

    @BeforeEach
    void setUp() {
        admin = testUsers.loginCookie(testUsers.create(Role.ADMIN));
        User labUser = testUsers.create(Role.LAB_TECHNICIAN);
        lab = testUsers.loginCookie(labUser);
        labUserId = labUser.getId();
        User doctorUser = testUsers.create(Role.DOCTOR);
        doctor = testUsers.loginCookie(doctorUser);
        reception = testUsers.loginCookie(testUsers.create(Role.RECEPTIONIST));
        User pathologistUser = testUsers.create(Role.PATHOLOGIST);
        pathologist = testUsers.loginCookie(pathologistUser);
        pathologistUserId = pathologistUser.getId();
        User patientUser = testUsers.create(Role.PATIENT);
        patient = testUsers.loginCookie(patientUser);
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

    /** Runs one CBC through order, collection, receipt, testing, entry and verification (which generates the report). */
    private String reportedCbc() throws Exception {
        String appointmentId = read(mvc.perform(json(post("/api/queue/tokens"),
                        "{\"patientId\":\"" + patientId + "\",\"doctorId\":\"" + doctorId + "\"}").cookie(reception))
                .andExpect(status().isCreated()).andReturn(), "$.id");
        mvc.perform(json(post("/api/consultations"), "{\"appointmentId\":\"" + appointmentId + "\"}").cookie(doctor))
                .andExpect(status().isOk());
        String orderId = read(mvc.perform(json(post("/api/lab-orders"), "{\"patientId\":\"" + patientId + "\",\"testIds\":[\""
                + testId("CBC") + "\"]}").cookie(doctor)).andExpect(status().isCreated()).andReturn(), "$.id");
        String sampleId = jdbc.queryForObject("SELECT id FROM samples WHERE lab_order_id = ?", UUID.class, UUID.fromString(orderId)).toString();
        mvc.perform(json(post("/api/samples/" + sampleId + "/collect"), "{\"tubeTypeUsed\":\"EDTA\",\"bodySite\":\"Left arm\"}").cookie(lab))
                .andExpect(status().isOk());
        mvc.perform(json(post("/api/samples/" + sampleId + "/receive"), "{\"accepted\":true}").cookie(lab)).andExpect(status().isOk());
        mvc.perform(post("/api/samples/" + sampleId + "/start-testing").header(CsrfHeaderFilter.HEADER, "1").cookie(lab))
                .andExpect(status().isOk());

        MvcResult sheet = mvc.perform(get("/api/samples/" + sampleId + "/results").cookie(lab)).andExpect(status().isOk()).andReturn();
        List<Map<String, Object>> parameters = JsonPath.read(sheet.getResponse().getContentAsString(), "$.sheet[*].parameters[*]");
        StringBuilder values = new StringBuilder("{\"analyzer\":\"Sysmex XN-1000\",\"values\":[");
        for (int i = 0; i < parameters.size(); i++) {
            values.append(i == 0 ? "" : ",").append("{\"parameterId\":\"").append(parameters.get(i).get("parameterId"))
                    .append("\",\"value\":\"").append("13").append("\"}");
        }
        mvc.perform(json(post("/api/samples/" + sampleId + "/results"), values.append("]}").toString()).cookie(lab))
                .andExpect(status().isOk());
        mvc.perform(post("/api/samples/" + sampleId + "/verify").header(CsrfHeaderFilter.HEADER, "1").cookie(pathologist))
                .andExpect(status().isOk());
        return sampleId;
    }

    /**
     * Puts the sample's events at known times before now (minutes), so turnaround can be asserted exactly:
     * collected 120, received 100, result entered 40, verified 10, report ready 5.
     */
    private void spaceOutEvents(String sampleId) {
        jdbc.execute("ALTER TABLE sample_status_events DISABLE TRIGGER sample_status_events_append_only");
        try {
            Map<String, Integer> minutesAgo = Map.of("COLLECTED", 120, "RECEIVED_AT_LAB", 100, "RESULT_ENTERED", 40, "VERIFIED", 10,
                    "REPORT_GENERATED", 5);
            minutesAgo.forEach((status, minutes) -> jdbc.update(
                    "UPDATE sample_status_events SET occurred_at = now() - make_interval(mins => ?) WHERE sample_id = ? AND status = ?",
                    minutes, UUID.fromString(sampleId), status));
        } finally {
            jdbc.execute("ALTER TABLE sample_status_events ENABLE TRIGGER sample_status_events_append_only");
        }
    }

    // ---------------------------------------------------------------- access and input

    @Test
    void onlyTheAdminSeesTheInsights() throws Exception {
        for (Cookie other : new Cookie[] {lab, doctor, reception, pathologist, patient}) {
            mvc.perform(get("/api/admin/dashboard").cookie(other)).andExpect(status().isForbidden());
            mvc.perform(get("/api/admin/analytics/tat").cookie(other)).andExpect(status().isForbidden());
        }
        mvc.perform(get("/api/admin/dashboard")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/dashboard").cookie(admin)).andExpect(status().isOk());
    }

    @Test
    void thePeriodIsValidated() throws Exception {
        mvc.perform(get("/api/admin/dashboard?days=0").cookie(admin)).andExpect(status().isBadRequest());
        mvc.perform(get("/api/admin/dashboard?days=181").cookie(admin)).andExpect(status().isBadRequest());
        mvc.perform(get("/api/admin/dashboard?days=180").cookie(admin)).andExpect(status().isOk());
        mvc.perform(get("/api/admin/analytics/tat?days=-3").cookie(admin)).andExpect(status().isBadRequest());
        mvc.perform(get("/api/admin/analytics/tat?testId=" + UUID.randomUUID()).cookie(admin)).andExpect(status().isNotFound());
    }

    // ---------------------------------------------------------------- an empty clinic

    @Test
    void aQuietPeriodHasZeroesForEveryDayAndNothingInvented() throws Exception {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Kolkata"));
        mvc.perform(get("/api/admin/dashboard?days=7").cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.days").value(7))
                .andExpect(jsonPath("$.to").value(today.toString()))
                .andExpect(jsonPath("$.from").value(today.minusDays(6).toString()))
                .andExpect(jsonPath("$.series", hasSize(7)))
                .andExpect(jsonPath("$.series[0].date").value(today.minusDays(6).toString()))
                .andExpect(jsonPath("$.series[6].date").value(today.toString()))
                .andExpect(jsonPath("$.series[3].patients").value(0))
                .andExpect(jsonPath("$.series[3].revenue").value(0))
                .andExpect(jsonPath("$.kpis.patientsInPeriod").value(0))
                .andExpect(jsonPath("$.kpis.reportsInPeriod").value(0))
                // No report yet, so no turnaround figure at all (not a zero).
                .andExpect(jsonPath("$.kpis.medianTatMinutes").doesNotExist())
                .andExpect(jsonPath("$.topTests", hasSize(0)))
                .andExpect(jsonPath("$.tat", hasSize(0)))
                .andExpect(jsonPath("$.heatmap", hasSize(0)));
    }

    // ---------------------------------------------------------------- a real flow

    @Test
    void turnaroundIsMeasuredFromTheSampleEventLog() throws Exception {
        String sampleId = reportedCbc();
        spaceOutEvents(sampleId);

        // Collected 120 min ago, report ready 5 min ago: 115 minutes; 20 to reach the lab, 60 testing, 30 to verify.
        mvc.perform(get("/api/admin/analytics/tat?days=7").cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tests", hasSize(1)))
                .andExpect(jsonPath("$.tests[0].code").value("CBC"))
                .andExpect(jsonPath("$.tests[0].samples").value(1))
                .andExpect(jsonPath("$.tests[0].avgMinutes").value(115))
                .andExpect(jsonPath("$.tests[0].medianMinutes").value(115))
                .andExpect(jsonPath("$.tests[0].p90Minutes").value(115))
                .andExpect(jsonPath("$.tests[0].toLabMinutes").value(20))
                .andExpect(jsonPath("$.tests[0].testingMinutes").value(60))
                .andExpect(jsonPath("$.tests[0].verificationMinutes").value(30))
                .andExpect(jsonPath("$.tests[0].retested").value(0))
                .andExpect(jsonPath("$.heatmap", hasSize(1)))
                .andExpect(jsonPath("$.heatmap[0].medianMinutes").value(115));

        // Filtering to a test with no reports gives an empty answer, not an error.
        mvc.perform(get("/api/admin/analytics/tat?days=7&testId=" + testId("ESR")).cookie(admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.tests", hasSize(0)));
        mvc.perform(get("/api/admin/analytics/tat?days=7&testId=" + testId("CBC")).cookie(admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.tests", hasSize(1)));
    }

    @Test
    void theDashboardCountsWhatHappened() throws Exception {
        String sampleId = reportedCbc();
        spaceOutEvents(sampleId);
        UUID invoiceId = jdbc.queryForObject("SELECT id FROM invoices WHERE lab_order_id IS NOT NULL LIMIT 1", UUID.class);
        UUID receptionUser = jdbc.queryForObject("SELECT id FROM users WHERE role = 'RECEPTIONIST' LIMIT 1", UUID.class);
        jdbc.update("INSERT INTO payments (invoice_id, amount, method, received_by_user_id) VALUES (?, 350, 'CASH', ?)",
                invoiceId, receptionUser);

        mvc.perform(get("/api/admin/dashboard?days=7").cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.series", hasSize(7)))
                .andExpect(jsonPath("$.series[6].reports").value(1))
                .andExpect(jsonPath("$.series[6].revenue").value(350.0))
                .andExpect(jsonPath("$.kpis.reportsInPeriod").value(1))
                .andExpect(jsonPath("$.kpis.reportsPrevious").value(0))
                .andExpect(jsonPath("$.kpis.revenueInPeriod").value(350.0))
                .andExpect(jsonPath("$.kpis.revenueToday").value(350.0))
                .andExpect(jsonPath("$.kpis.medianTatMinutes").value(115))
                .andExpect(jsonPath("$.topTests[0].code").value("CBC"))
                .andExpect(jsonPath("$.topTests[0].orders").value(1))
                .andExpect(jsonPath("$.tat[0].code").value("CBC"));

        // Work is credited to the person who did it.
        MvcResult result = mvc.perform(get("/api/admin/dashboard?days=7").cookie(admin)).andReturn();
        List<Map<String, Object>> staff = JsonPath.read(result.getResponse().getContentAsString(), "$.staff");
        Map<String, Object> technician = staff.stream().filter(s -> s.get("userId").equals(labUserId.toString())).findFirst().orElseThrow();
        @SuppressWarnings("unchecked")
        Map<String, Integer> techMeasures = (Map<String, Integer>) technician.get("measures");
        assertThat(techMeasures).containsEntry("Samples collected", 1).containsEntry("Samples received", 1)
                .containsEntry("Results entered", 1);
        Map<String, Object> verifier = staff.stream().filter(s -> s.get("userId").equals(pathologistUserId.toString())).findFirst().orElseThrow();
        @SuppressWarnings("unchecked")
        Map<String, Integer> verifierMeasures = (Map<String, Integer>) verifier.get("measures");
        assertThat(verifierMeasures).containsEntry("Results verified", 1);
    }

    @Test
    void periodsOutsideTheWindowAreLeftOut() throws Exception {
        String sampleId = reportedCbc();
        spaceOutEvents(sampleId);
        // Pretend the report was made 10 days ago: it drops out of a 7-day view but shows in a 30-day one.
        jdbc.execute("ALTER TABLE sample_status_events DISABLE TRIGGER sample_status_events_append_only");
        try {
            jdbc.update("UPDATE sample_status_events SET occurred_at = occurred_at - interval '10 days' WHERE sample_id = ?",
                    UUID.fromString(sampleId));
        } finally {
            jdbc.execute("ALTER TABLE sample_status_events ENABLE TRIGGER sample_status_events_append_only");
        }
        mvc.perform(get("/api/admin/analytics/tat?days=7").cookie(admin)).andExpect(jsonPath("$.tests", hasSize(0)));
        mvc.perform(get("/api/admin/analytics/tat?days=30").cookie(admin))
                .andExpect(jsonPath("$.tests", hasSize(1))).andExpect(jsonPath("$.tests[0].medianMinutes").value(115));
    }
}
