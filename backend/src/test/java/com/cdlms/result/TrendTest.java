package com.cdlms.result;

import com.cdlms.auth.CsrfHeaderFilter;
import com.cdlms.result.RangeCheck.Flag;
import com.cdlms.result.ResultDtos.TrendSeries;
import com.cdlms.result.ResultQueries.TrendRow;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Phase 10: a patient's numbers across visits, seen by the patient, their doctor and the pathologist (ADR-029). */
class TrendTest extends IntegrationTest {

    private Cookie reception;
    private Cookie doctor;
    private Cookie otherDoctor;
    private Cookie patient;
    private Cookie otherPatient;
    private Cookie lab;
    private Cookie pathologist;
    private Cookie admin;
    private UUID doctorId;
    private UUID patientId;
    private boolean consulted;

    @BeforeEach
    void setUp() {
        reception = testUsers.loginCookie(testUsers.create(Role.RECEPTIONIST));
        User doctorUser = testUsers.create(Role.DOCTOR);
        doctor = testUsers.loginCookie(doctorUser);
        otherDoctor = testUsers.loginCookie(testUsers.create(Role.DOCTOR, "other.doctor@test.local"));
        User patientUser = testUsers.create(Role.PATIENT);
        patient = testUsers.loginCookie(patientUser);
        otherPatient = testUsers.loginCookie(testUsers.create(Role.PATIENT, "other.patient@test.local"));
        lab = testUsers.loginCookie(testUsers.create(Role.LAB_TECHNICIAN));
        pathologist = testUsers.loginCookie(testUsers.create(Role.PATHOLOGIST));
        admin = testUsers.loginCookie(testUsers.create(Role.ADMIN));
        doctorId = jdbc.queryForObject("SELECT id FROM doctors WHERE user_id = ?", UUID.class, doctorUser.getId());
        patientId = jdbc.queryForObject("SELECT id FROM patients WHERE user_id = ?", UUID.class, patientUser.getId());
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder builder, String body) {
        return builder.header(CsrfHeaderFilter.HEADER, "1").contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private static String read(MvcResult result, String path) throws Exception {
        Object value = JsonPath.read(result.getResponse().getContentAsString(), path);
        return value == null ? null : value.toString();
    }

    /** Takes one test to a verified report with the given haemoglobin (CBC only); other values are normal. */
    private String reported(String testCode, String tube, String haemoglobin) throws Exception {
        if (!consulted) {
            String appointmentId = read(mvc.perform(json(post("/api/queue/tokens"),
                            "{\"patientId\":\"" + patientId + "\",\"doctorId\":\"" + doctorId + "\"}").cookie(reception))
                    .andExpect(status().isCreated()).andReturn(), "$.id");
            mvc.perform(json(post("/api/consultations"), "{\"appointmentId\":\"" + appointmentId + "\"}").cookie(doctor))
                    .andExpect(status().isOk());
            consulted = true;
        }
        String testId = jdbc.queryForObject("SELECT id FROM lab_tests WHERE code = ?", UUID.class, testCode).toString();
        String orderId = read(mvc.perform(json(post("/api/lab-orders"), "{\"patientId\":\"" + patientId + "\",\"testIds\":[\""
                + testId + "\"]}").cookie(doctor)).andExpect(status().isCreated()).andReturn(), "$.id");
        String sampleId = jdbc.queryForObject("SELECT id FROM samples WHERE lab_order_id = ?", UUID.class, UUID.fromString(orderId)).toString();
        mvc.perform(json(post("/api/samples/" + sampleId + "/collect"), "{\"tubeTypeUsed\":\"" + tube + "\",\"bodySite\":\"Left arm\"}").cookie(lab))
                .andExpect(status().isOk());
        mvc.perform(json(post("/api/samples/" + sampleId + "/receive"), "{\"accepted\":true}").cookie(lab)).andExpect(status().isOk());
        mvc.perform(post("/api/samples/" + sampleId + "/start-testing").header(CsrfHeaderFilter.HEADER, "1").cookie(lab))
                .andExpect(status().isOk());
        MvcResult sheet = mvc.perform(get("/api/samples/" + sampleId + "/results").cookie(lab)).andExpect(status().isOk()).andReturn();
        List<Map<String, Object>> parameters = JsonPath.read(sheet.getResponse().getContentAsString(), "$.sheet[*].parameters[*]");
        Map<String, String> byName = new HashMap<>(Map.of("Haemoglobin", haemoglobin, "Total WBC count", "7.5", "Platelet count", "250",
                "RBC count", "4.8", "Haematocrit", "40"));
        StringBuilder values = new StringBuilder("{\"analyzer\":\"Sysmex\",\"values\":[");
        for (int i = 0; i < parameters.size(); i++) {
            String fallback = "TEXT".equals(parameters.get(i).get("valueType")) ? "Negative" : "1";
            values.append(i == 0 ? "" : ",").append("{\"parameterId\":\"").append(parameters.get(i).get("parameterId"))
                    .append("\",\"value\":\"").append(byName.getOrDefault((String) parameters.get(i).get("name"), fallback)).append("\"}");
        }
        mvc.perform(json(post("/api/samples/" + sampleId + "/results"), values.append("]}").toString()).cookie(lab)).andExpect(status().isOk());
        mvc.perform(post("/api/samples/" + sampleId + "/verify").header(CsrfHeaderFilter.HEADER, "1").cookie(pathologist))
                .andExpect(status().isOk());
        return sampleId;
    }

    /** Moves a result's verification time so visits are days apart and their order is certain. */
    private void verifiedDaysAgo(String sampleId, int days) {
        jdbc.execute("ALTER TABLE sample_results DISABLE TRIGGER sample_results_transition");
        try {
            jdbc.update("UPDATE sample_results SET verified_at = now() - make_interval(days => ?) WHERE sample_id = ? AND status = 'VERIFIED'",
                    days, UUID.fromString(sampleId));
        } finally {
            jdbc.execute("ALTER TABLE sample_results ENABLE TRIGGER sample_results_transition");
        }
    }

    private void dispatch(String sampleId) throws Exception {
        mvc.perform(json(post("/api/reports/" + sampleId + "/dispatch"), "{\"channel\":\"DOWNLOAD_LINK\"}").cookie(lab)).andExpect(status().isOk());
    }

    /** Three CBCs at 10, 6.5 and 12.6 g/dL, 60, 30 and 5 days ago, plus one ESR. */
    private String[] threeVisits() throws Exception {
        String a = reported("CBC", "EDTA", "10");
        String b = reported("CBC", "EDTA", "6.5");
        String c = reported("CBC", "EDTA", "12.6");
        reported("ESR", "EDTA", "0");
        verifiedDaysAgo(a, 60);
        verifiedDaysAgo(b, 30);
        verifiedDaysAgo(c, 5);
        return new String[] {a, b, c};
    }

    // ---------------------------------------------------------------- what a trend is

    @Test
    void aParameterMeasuredOnSeveralVisitsBecomesASeriesOldestFirst() throws Exception {
        String[] visits = threeVisits();

        MvcResult result = mvc.perform(get("/api/patients/" + patientId + "/trends").cookie(doctor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.patientId").value(patientId.toString()))
                // The five CBC parameters were measured three times; the one-off ESR makes no trend.
                .andExpect(jsonPath("$.series", hasSize(5)))
                .andExpect(jsonPath("$.series[?(@.testCode == 'ESR')]", hasSize(0)))
                .andReturn();
        Map<String, Object> hb = haemoglobin(result);
        assertThat(hb.get("unit")).isEqualTo("g/dL");
        assertThat(((Number) hb.get("refLow")).doubleValue()).isEqualTo(12.0);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> points = (List<Map<String, Object>>) hb.get("points");
        assertThat(points).extracting(p -> ((Number) p.get("value")).doubleValue()).containsExactly(10.0, 6.5, 12.6);
        assertThat(points).extracting(p -> p.get("sampleId")).containsExactly(visits[0], visits[1], visits[2]);
        assertThat(points).extracting(p -> p.get("flag")).containsExactly("LOW", "CRITICAL_LOW", "NORMAL");
    }

    private static Map<String, Object> haemoglobin(MvcResult result) throws Exception {
        List<Map<String, Object>> found = JsonPath.read(result.getResponse().getContentAsString(), "$.series[?(@.name == 'Haemoglobin')]");
        assertThat(found).hasSize(1);
        return found.getFirst();
    }

    @Test
    void aPatientSeesOnlyValuesFromReportsThatWereSentToThem() throws Exception {
        String[] visits = threeVisits();
        dispatch(visits[0]);
        dispatch(visits[2]);

        MvcResult result = mvc.perform(get("/api/patients/" + patientId + "/trends").cookie(patient)).andExpect(status().isOk())
                .andExpect(jsonPath("$.series", hasSize(5))).andReturn();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> points = (List<Map<String, Object>>) haemoglobin(result).get("points");
        // The 6.5 from the report that hasn't been sent yet is not shown.
        assertThat(points).extracting(p -> p.get("sampleId")).containsExactly(visits[0], visits[2]);
    }

    @Test
    void oneSentReportMakesNoTrendForThePatientYet() throws Exception {
        String[] visits = threeVisits();
        dispatch(visits[1]);
        mvc.perform(get("/api/patients/" + patientId + "/trends").cookie(patient))
                .andExpect(status().isOk()).andExpect(jsonPath("$.series", hasSize(0)));
    }

    // ---------------------------------------------------------------- who may look

    @Test
    void aDoctorNeedsACareRelationshipAndIsLogged() throws Exception {
        threeVisits();
        long before = jdbc.queryForObject("SELECT count(*) FROM patient_access_log WHERE resource = 'LAB_HISTORY'", Long.class);

        mvc.perform(get("/api/patients/" + patientId + "/trends").cookie(doctor)).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM patient_access_log WHERE resource = 'LAB_HISTORY'", Long.class))
                .isEqualTo(before + 1);

        mvc.perform(get("/api/patients/" + patientId + "/trends").cookie(otherDoctor))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("NO_CARE_RELATIONSHIP"));
        mvc.perform(get("/api/patients/" + UUID.randomUUID() + "/trends").cookie(doctor)).andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM patient_access_log WHERE resource = 'LAB_HISTORY'", Long.class))
                .isEqualTo(before + 1);
    }

    @Test
    void aPathologistSeesEverythingVerifiedAndIsLogged() throws Exception {
        threeVisits();
        mvc.perform(get("/api/patients/" + patientId + "/trends").cookie(pathologist))
                .andExpect(status().isOk()).andExpect(jsonPath("$.series", hasSize(5)));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM patient_access_log WHERE resource = 'LAB_HISTORY' AND user_id IN "
                + "(SELECT id FROM users WHERE role = 'PATHOLOGIST')", Long.class)).isEqualTo(1);
    }

    @Test
    void nobodyElseCanSeeAPatientsTrends() throws Exception {
        threeVisits();
        String url = "/api/patients/" + patientId + "/trends";
        mvc.perform(get(url).cookie(otherPatient)).andExpect(status().isNotFound());
        mvc.perform(get(url).cookie(lab)).andExpect(status().isForbidden());
        mvc.perform(get(url).cookie(admin)).andExpect(status().isForbidden());
        mvc.perform(get(url).cookie(reception)).andExpect(status().isForbidden());
        mvc.perform(get(url)).andExpect(status().isUnauthorized());
    }

    // ---------------------------------------------------------------- grouping, without a database

    private static TrendRow row(UUID parameter, String value, int day) {
        return new TrendRow(parameter, "Haemoglobin", "g/dL", "CBC", "Complete Blood Count", new BigDecimal(value), Flag.NORMAL,
                new BigDecimal("12"), new BigDecimal("15.5"), new BigDecimal("7"), new BigDecimal("20"), UUID.randomUUID(),
                "LAB-" + day, Instant.parse("2026-01-01T00:00:00Z").plusSeconds(day * 86_400L));
    }

    @Test
    void seriesKeepTheLatestTwelveAndDropSingleValues() {
        UUID many = UUID.randomUUID();
        UUID one = UUID.randomUUID();
        List<TrendRow> rows = new ArrayList<>();
        for (int day = 1; day <= 15; day++) {
            rows.add(row(many, String.valueOf(10 + day), day));
        }
        rows.add(row(one, "13", 1));

        List<TrendSeries> series = TrendService.series(rows);

        assertThat(series).hasSize(1);
        assertThat(series.getFirst().parameterId()).isEqualTo(many);
        assertThat(series.getFirst().points()).hasSize(12);
        assertThat(series.getFirst().points().getFirst().value()).isEqualByComparingTo("14");
        assertThat(series.getFirst().points().getLast().value()).isEqualByComparingTo("25");
    }
}
