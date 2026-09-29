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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Phase 10: critical results reach the ordering doctor and stay until acknowledged (ADR-028). */
class CriticalAlertTest extends IntegrationTest {

    private Cookie reception;
    private Cookie doctor;
    private Cookie otherDoctor;
    private Cookie patient;
    private Cookie lab;
    private Cookie pathologist;
    private Cookie admin;
    private UUID doctorId;
    private UUID patientId;

    @BeforeEach
    void setUp() {
        reception = testUsers.loginCookie(testUsers.create(Role.RECEPTIONIST));
        User doctorUser = testUsers.create(Role.DOCTOR);
        doctor = testUsers.loginCookie(doctorUser);
        otherDoctor = testUsers.loginCookie(testUsers.create(Role.DOCTOR, "other.doctor@test.local"));
        User patientUser = testUsers.create(Role.PATIENT);
        patient = testUsers.loginCookie(patientUser);
        lab = testUsers.loginCookie(testUsers.create(Role.LAB_TECHNICIAN));
        pathologist = testUsers.loginCookie(testUsers.create(Role.PATHOLOGIST));
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

    /** CBC values that are all normal, except what is overridden. Hb 6.5 is at or below the critical limit (7.0). */
    private Map<String, String> cbc(String haemoglobin) {
        return new HashMap<>(Map.of("Haemoglobin", haemoglobin, "Total WBC count", "7.5", "Platelet count", "250",
                "RBC count", "4.8", "Haematocrit", "40"));
    }

    private boolean consulted;

    /** A CBC taken to a verified report with the given haemoglobin; returns the sample id. */
    private String reportWithHb(String haemoglobin) throws Exception {
        if (!consulted) {
            String appointmentId = read(mvc.perform(json(post("/api/queue/tokens"),
                            "{\"patientId\":\"" + patientId + "\",\"doctorId\":\"" + doctorId + "\"}").cookie(reception))
                    .andExpect(status().isCreated()).andReturn(), "$.id");
            mvc.perform(json(post("/api/consultations"), "{\"appointmentId\":\"" + appointmentId + "\"}").cookie(doctor))
                    .andExpect(status().isOk());
            consulted = true;
        }
        String testId = jdbc.queryForObject("SELECT id FROM lab_tests WHERE code = 'CBC'", UUID.class).toString();
        String orderId = read(mvc.perform(json(post("/api/lab-orders"), "{\"patientId\":\"" + patientId + "\",\"testIds\":[\""
                + testId + "\"]}").cookie(doctor)).andExpect(status().isCreated()).andReturn(), "$.id");
        String sampleId = jdbc.queryForObject("SELECT id FROM samples WHERE lab_order_id = ?", UUID.class, UUID.fromString(orderId)).toString();
        mvc.perform(json(post("/api/samples/" + sampleId + "/collect"), "{\"tubeTypeUsed\":\"EDTA\",\"bodySite\":\"Left arm\"}").cookie(lab))
                .andExpect(status().isOk());
        mvc.perform(json(post("/api/samples/" + sampleId + "/receive"), "{\"accepted\":true}").cookie(lab)).andExpect(status().isOk());
        mvc.perform(post("/api/samples/" + sampleId + "/start-testing").header(CsrfHeaderFilter.HEADER, "1").cookie(lab))
                .andExpect(status().isOk());
        MvcResult sheet = mvc.perform(get("/api/samples/" + sampleId + "/results").cookie(lab)).andExpect(status().isOk()).andReturn();
        List<Map<String, Object>> parameters = JsonPath.read(sheet.getResponse().getContentAsString(), "$.sheet[*].parameters[*]");
        Map<String, String> byName = cbc(haemoglobin);
        StringBuilder values = new StringBuilder("{\"analyzer\":\"Sysmex\",\"values\":[");
        for (int i = 0; i < parameters.size(); i++) {
            values.append(i == 0 ? "" : ",").append("{\"parameterId\":\"").append(parameters.get(i).get("parameterId"))
                    .append("\",\"value\":\"").append(byName.getOrDefault((String) parameters.get(i).get("name"), "1")).append("\"}");
        }
        mvc.perform(json(post("/api/samples/" + sampleId + "/results"), values.append("]}").toString()).cookie(lab))
                .andExpect(status().isOk());
        mvc.perform(post("/api/samples/" + sampleId + "/verify").header(CsrfHeaderFilter.HEADER, "1").cookie(pathologist))
                .andExpect(status().isOk());
        return sampleId;
    }

    private void acknowledge(Cookie who, String sampleId, String body, int expected) throws Exception {
        mvc.perform(json(post("/api/reports/" + sampleId + "/acknowledge-critical"), body).cookie(who))
                .andExpect(status().is(expected));
    }

    // ---------------------------------------------------------------- flagging

    @Test
    void aCriticalReportIsMarkedCriticalAndAnotherIsNot() throws Exception {
        String critical = reportWithHb("6.5");
        String normal = reportWithHb("13.2");
        assertThat(jdbc.queryForObject("SELECT is_critical FROM reports WHERE sample_id = ?", Boolean.class, UUID.fromString(critical))).isTrue();
        assertThat(jdbc.queryForObject("SELECT is_critical FROM reports WHERE sample_id = ?", Boolean.class, UUID.fromString(normal))).isFalse();
    }

    @Test
    void theOrderingDoctorSeesTheirCriticalResultsWithWhichValue() throws Exception {
        String sampleId = reportWithHb("6.5");
        reportWithHb("13.2");

        mvc.perform(get("/api/reports/critical").cookie(doctor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.open").value(1))
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].sampleId").value(sampleId))
                .andExpect(jsonPath("$.items[0].patientName").value("Test PATIENT"))
                .andExpect(jsonPath("$.items[0].testNames[0]").value("Complete Blood Count"))
                .andExpect(jsonPath("$.items[0].parameters", hasSize(1)))
                .andExpect(jsonPath("$.items[0].parameters[0].name").value("Haemoglobin"))
                .andExpect(jsonPath("$.items[0].parameters[0].flag").value("CRITICAL_LOW"))
                // The number itself stays on the report, which is logged when opened.
                .andExpect(jsonPath("$.items[0].parameters[0].value").doesNotExist());

        // A different doctor was not the one who ordered it.
        mvc.perform(get("/api/reports/critical").cookie(otherDoctor))
                .andExpect(status().isOk()).andExpect(jsonPath("$.open").value(0)).andExpect(jsonPath("$.items", hasSize(0)));
    }

    // ---------------------------------------------------------------- who sees what

    @Test
    void theLabSeesEveryWaitingOneAndTheAdminOnlyACount() throws Exception {
        reportWithHb("6.5");

        mvc.perform(get("/api/reports/critical/all").cookie(lab))
                .andExpect(status().isOk()).andExpect(jsonPath("$.open").value(1))
                .andExpect(jsonPath("$.items[0].orderingDoctor").value("Test DOCTOR"));

        String summary = mvc.perform(get("/api/reports/critical/summary").cookie(admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.open").value(1)).andExpect(jsonPath("$.oldestVerifiedAt").exists())
                .andReturn().getResponse().getContentAsString();
        assertThat(summary).doesNotContain("Test PATIENT").doesNotContain("Haemoglobin").doesNotContain("items");
    }

    @Test
    void otherRolesAreTurnedAway() throws Exception {
        mvc.perform(get("/api/reports/critical").cookie(lab)).andExpect(status().isForbidden());
        mvc.perform(get("/api/reports/critical").cookie(admin)).andExpect(status().isForbidden());
        mvc.perform(get("/api/reports/critical/all").cookie(doctor)).andExpect(status().isForbidden());
        mvc.perform(get("/api/reports/critical/all").cookie(admin)).andExpect(status().isForbidden());
        mvc.perform(get("/api/reports/critical/summary").cookie(lab)).andExpect(status().isForbidden());
        for (Cookie other : new Cookie[] {patient, reception, pathologist}) {
            mvc.perform(get("/api/reports/critical").cookie(other)).andExpect(status().isForbidden());
            mvc.perform(get("/api/reports/critical/all").cookie(other)).andExpect(status().isForbidden());
            mvc.perform(get("/api/reports/critical/summary").cookie(other)).andExpect(status().isForbidden());
        }
        mvc.perform(get("/api/reports/critical")).andExpect(status().isUnauthorized());
    }

    // ---------------------------------------------------------------- acknowledging

    @Test
    void acknowledgingRecordsWhoAndWhenAndClearsTheAlert() throws Exception {
        String sampleId = reportWithHb("6.5");
        long logged = jdbc.queryForObject("SELECT count(*) FROM patient_access_log", Long.class);

        mvc.perform(json(post("/api/reports/" + sampleId + "/acknowledge-critical"), "{\"note\":\"Phoned the patient, sent to ED\"}").cookie(doctor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sampleId").value(sampleId))
                .andExpect(jsonPath("$.acknowledgedBy").value("Test DOCTOR"))
                .andExpect(jsonPath("$.acknowledgedAt").exists());

        mvc.perform(get("/api/reports/critical").cookie(doctor)).andExpect(jsonPath("$.open").value(0));
        mvc.perform(get("/api/reports/critical/all").cookie(lab)).andExpect(jsonPath("$.open").value(0));
        mvc.perform(get("/api/reports/critical/summary").cookie(admin)).andExpect(jsonPath("$.open").value(0));
        mvc.perform(get("/api/reports/" + sampleId).cookie(doctor))
                .andExpect(jsonPath("$.critical").value(true))
                .andExpect(jsonPath("$.criticalAcknowledgedBy").value("Test DOCTOR"))
                .andExpect(jsonPath("$.criticalAcknowledgedAt").exists());
        assertThat(jdbc.queryForObject("SELECT critical_ack_note FROM reports WHERE sample_id = ?", String.class, UUID.fromString(sampleId)))
                .isEqualTo("Phoned the patient, sent to ED");
        // Acknowledging is a doctor reading the report, so it is logged like opening it.
        assertThat(jdbc.queryForObject("SELECT count(*) FROM patient_access_log", Long.class)).isGreaterThan(logged);
    }

    @Test
    void aNoteIsOptionalButLimited() throws Exception {
        String first = reportWithHb("6.5");
        acknowledge(doctor, first, "{}", 200);
        String second = reportWithHb("6.4");
        acknowledge(doctor, second, "{\"note\":\"" + "x".repeat(301) + "\"}", 400);
        mvc.perform(post("/api/reports/" + second + "/acknowledge-critical").header(CsrfHeaderFilter.HEADER, "1").cookie(doctor))
                .andExpect(status().isOk());
    }

    @Test
    void itCanOnlyBeAcknowledgedOnceAndOnlyByTheOrderingDoctor() throws Exception {
        String sampleId = reportWithHb("6.5");
        acknowledge(otherDoctor, sampleId, "{}", 404);
        acknowledge(lab, sampleId, "{}", 403);
        acknowledge(admin, sampleId, "{}", 403);
        acknowledge(patient, sampleId, "{}", 403);
        acknowledge(doctor, sampleId, "{\"note\":\"seen\"}", 200);
        mvc.perform(json(post("/api/reports/" + sampleId + "/acknowledge-critical"), "{}").cookie(doctor))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ALREADY_ACKNOWLEDGED"));
        assertThat(jdbc.queryForObject("SELECT critical_ack_note FROM reports WHERE sample_id = ?", String.class, UUID.fromString(sampleId)))
                .isEqualTo("seen");
    }

    @Test
    void aNormalReportHasNothingToAcknowledge() throws Exception {
        String sampleId = reportWithHb("13.2");
        mvc.perform(json(post("/api/reports/" + sampleId + "/acknowledge-critical"), "{}").cookie(doctor))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("NOT_CRITICAL"));
        mvc.perform(json(post("/api/reports/" + UUID.randomUUID() + "/acknowledge-critical"), "{}").cookie(doctor))
                .andExpect(status().isNotFound());
    }

    @Test
    void twoAcknowledgementsAtOnceRecordExactlyOne() throws Exception {
        String sampleId = reportWithHb("6.5");
        ExecutorService pool = Executors.newFixedThreadPool(4);
        CountDownLatch go = new CountDownLatch(1);
        try {
            List<Future<Integer>> attempts = new java.util.ArrayList<>();
            for (int i = 0; i < 4; i++) {
                attempts.add(pool.submit(() -> {
                    go.await();
                    return mvc.perform(json(post("/api/reports/" + sampleId + "/acknowledge-critical"), "{\"note\":\"seen\"}").cookie(doctor))
                            .andReturn().getResponse().getStatus();
                }));
            }
            go.countDown();
            int ok = 0;
            int conflict = 0;
            for (Future<Integer> attempt : attempts) {
                int code = attempt.get();
                if (code == 200) {
                    ok++;
                } else if (code == 409) {
                    conflict++;
                }
            }
            assertThat(ok).isEqualTo(1);
            assertThat(conflict).isEqualTo(3);
        } finally {
            pool.shutdownNow();
        }
    }

    // ---------------------------------------------------------------- the database's part

    @Test
    void theDatabaseKeepsTheAcknowledgementAndTheCriticalFlagFixed() throws Exception {
        String critical = reportWithHb("6.5");
        String normal = reportWithHb("13.2");
        UUID criticalId = UUID.fromString(critical);
        UUID normalId = UUID.fromString(normal);
        UUID doctorUser = jdbc.queryForObject("SELECT user_id FROM doctors WHERE id = ?", UUID.class, doctorId);

        // Only a critical report can be acknowledged.
        assertThatThrownBy(() -> jdbc.update("UPDATE reports SET critical_acknowledged_at = now(), critical_acknowledged_by_user_id = ? WHERE sample_id = ?",
                doctorUser, normalId)).isInstanceOf(DataAccessException.class);
        // An acknowledgement needs a person.
        assertThatThrownBy(() -> jdbc.update("UPDATE reports SET critical_acknowledged_at = now() WHERE sample_id = ?", criticalId))
                .isInstanceOf(DataAccessException.class);
        // Whether a report is critical can't be edited.
        assertThatThrownBy(() -> jdbc.update("UPDATE reports SET is_critical = false WHERE sample_id = ?", criticalId))
                .isInstanceOf(DataAccessException.class);

        acknowledge(doctor, critical, "{\"note\":\"seen\"}", 200);
        // Once acknowledged it can't be changed or cleared.
        assertThatThrownBy(() -> jdbc.update("UPDATE reports SET critical_ack_note = 'changed' WHERE sample_id = ?", criticalId))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE reports SET critical_acknowledged_at = NULL, critical_acknowledged_by_user_id = NULL WHERE sample_id = ?",
                criticalId)).isInstanceOf(DataAccessException.class);
    }

    // ---------------------------------------------------------------- dashboards

    @Test
    void dashboardsShowTheAlertUntilItIsAcknowledged() throws Exception {
        String sampleId = reportWithHb("6.5");

        mvc.perform(get("/api/dashboard/doctor").cookie(doctor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.widgets[0].type").value("criticalResults"))
                .andExpect(jsonPath("$.widgets[0].data.open").value(1))
                .andExpect(jsonPath("$.widgets[0].data.items[0].sampleId").value(sampleId));
        mvc.perform(get("/api/dashboard/lab-technician").cookie(lab))
                .andExpect(jsonPath("$.widgets[0].type").value("criticalResults"))
                .andExpect(jsonPath("$.widgets[0].data.open").value(1));
        mvc.perform(get("/api/dashboard/admin").cookie(admin))
                .andExpect(jsonPath("$.widgets[?(@.type == 'criticalSummary')].data.open").value(1));
        // A doctor who ordered nothing gets no card.
        mvc.perform(get("/api/dashboard/doctor").cookie(otherDoctor))
                .andExpect(jsonPath("$.widgets[?(@.type == 'criticalResults')]", hasSize(0)));

        acknowledge(doctor, sampleId, "{}", 200);
        mvc.perform(get("/api/dashboard/doctor").cookie(doctor)).andExpect(jsonPath("$.widgets[0].type", not("criticalResults")));
        mvc.perform(get("/api/dashboard/lab-technician").cookie(lab)).andExpect(jsonPath("$.widgets[0].type", not("criticalResults")));
        mvc.perform(get("/api/dashboard/admin").cookie(admin))
                .andExpect(jsonPath("$.widgets[?(@.type == 'criticalSummary')]", hasSize(0)));
    }
}
