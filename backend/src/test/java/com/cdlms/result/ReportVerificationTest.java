package com.cdlms.result;

import com.cdlms.auth.CsrfHeaderFilter;
import com.cdlms.support.IntegrationTest;
import com.cdlms.user.Role;
import com.cdlms.user.User;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Phase 10: a printed report can be checked for authenticity by anyone, without seeing results (ADR-027). */
class ReportVerificationTest extends IntegrationTest {

    private Cookie reception;
    private Cookie doctor;
    private Cookie patient;
    private Cookie lab;
    private Cookie pathologist;
    private UUID doctorId;
    private UUID patientId;

    @BeforeEach
    void setUp() {
        reception = testUsers.loginCookie(testUsers.create(Role.RECEPTIONIST));
        User doctorUser = testUsers.create(Role.DOCTOR);
        doctor = testUsers.loginCookie(doctorUser);
        User patientUser = testUsers.create(Role.PATIENT);
        patient = testUsers.loginCookie(patientUser);
        lab = testUsers.loginCookie(testUsers.create(Role.LAB_TECHNICIAN));
        pathologist = testUsers.loginCookie(testUsers.create(Role.PATHOLOGIST));
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

    /** One CBC taken all the way to a verified report; returns the sample id. */
    private String reportedCbc() throws Exception {
        String appointmentId = read(mvc.perform(json(post("/api/queue/tokens"),
                        "{\"patientId\":\"" + patientId + "\",\"doctorId\":\"" + doctorId + "\"}").cookie(reception))
                .andExpect(status().isCreated()).andReturn(), "$.id");
        mvc.perform(json(post("/api/consultations"), "{\"appointmentId\":\"" + appointmentId + "\"}").cookie(doctor))
                .andExpect(status().isOk());
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
        StringBuilder values = new StringBuilder("{\"analyzer\":\"Sysmex\",\"values\":[");
        for (int i = 0; i < parameters.size(); i++) {
            values.append(i == 0 ? "" : ",").append("{\"parameterId\":\"").append(parameters.get(i).get("parameterId"))
                    .append("\",\"value\":\"13\"}");
        }
        mvc.perform(json(post("/api/samples/" + sampleId + "/results"), values.append("]}").toString()).cookie(lab))
                .andExpect(status().isOk());
        mvc.perform(post("/api/samples/" + sampleId + "/verify").header(CsrfHeaderFilter.HEADER, "1").cookie(pathologist))
                .andExpect(status().isOk());
        return sampleId;
    }

    private String codeOf(String sampleId) throws Exception {
        return read(mvc.perform(get("/api/reports/" + sampleId).cookie(doctor)).andExpect(status().isOk()).andReturn(), "$.verificationCode");
    }

    @Test
    void everyReportGetsARandomCodeOfTheRightShape() throws Exception {
        String sampleId = reportedCbc();
        String code = codeOf(sampleId);
        assertThat(code).matches("[0-9a-f]{32}");
        assertThat(jdbc.queryForObject("SELECT verification_code FROM reports WHERE sample_id = ?", String.class, UUID.fromString(sampleId)))
                .isEqualTo(code);
        assertThat(Report.newVerificationCode()).matches("[0-9a-f]{32}").isNotEqualTo(Report.newVerificationCode());
    }

    @Test
    void anyoneWithTheCodeCanCheckTheReportWithoutSigningIn() throws Exception {
        String sampleId = reportedCbc();
        String code = codeOf(sampleId);
        String sampleCode = jdbc.queryForObject("SELECT sample_code FROM samples WHERE id = ?", String.class, UUID.fromString(sampleId));

        MvcResult result = mvc.perform(get("/api/public/reports/" + code))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authentic").value(true))
                .andExpect(jsonPath("$.reportNumber").value(sampleCode))
                .andExpect(jsonPath("$.patientInitials").value("T. P."))
                .andExpect(jsonPath("$.tests", hasSize(1)))
                .andExpect(jsonPath("$.tests[0]").value("Complete Blood Count"))
                .andExpect(jsonPath("$.verifier.name").value("Test PATHOLOGIST"))
                .andExpect(jsonPath("$.verifiedAt").exists())
                .andReturn();

        // What it must not give away: results, the patient's name or any of their IDs.
        String body = result.getResponse().getContentAsString();
        assertThat(body).doesNotContain("Haemoglobin").doesNotContain("Test PATIENT").doesNotContain(patientId.toString())
                .doesNotContain("numericValue").doesNotContain("flag");
        assertThat(result.getResponse().getHeader("Cache-Control")).contains("no-store");
    }

    @Test
    void anythingThatIsNotARealCodeIsNotFound() throws Exception {
        reportedCbc();
        mvc.perform(get("/api/public/reports/" + "0".repeat(32))).andExpect(status().isNotFound());
        mvc.perform(get("/api/public/reports/not-a-code")).andExpect(status().isNotFound());
        mvc.perform(get("/api/public/reports/{code}", "' OR 1=1--")).andExpect(status().isNotFound());
    }

    @Test
    void theCodeCannotBeChangedAfterTheReportIsIssued() throws Exception {
        String sampleId = reportedCbc();
        assertThatThrownBy(() -> jdbc.update("UPDATE reports SET verification_code = ? WHERE sample_id = ?",
                "f".repeat(32), UUID.fromString(sampleId))).isInstanceOf(DataAccessException.class);
        // A second report can't reuse a code either.
        assertThatThrownBy(() -> jdbc.update("INSERT INTO reports (sample_id, generated_by_user_id, verification_code) "
                + "SELECT sample_id, generated_by_user_id, verification_code FROM reports WHERE sample_id = ?",
                UUID.fromString(sampleId))).isInstanceOf(DataAccessException.class);
    }

    @Test
    void theReportPdfPrintsTheCheckAddressAndAQrCode() throws Exception {
        String sampleId = reportedCbc();
        String code = codeOf(sampleId);
        mvc.perform(post("/api/reports/" + sampleId + "/dispatch").header(CsrfHeaderFilter.HEADER, "1")
                .contentType(MediaType.APPLICATION_JSON).content("{\"channel\":\"DOWNLOAD_LINK\"}").cookie(lab)).andExpect(status().isOk());

        byte[] pdf = mvc.perform(get("/api/reports/" + sampleId + "/pdf").cookie(patient)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        try (PDDocument document = Loader.loadPDF(pdf)) {
            String text = new PDFTextStripper().getText(document).replaceAll("\\s+", "");
            assertThat(text).contains("/verify/" + code);
            PDResources resources = document.getPage(0).getResources();
            long images = 0;
            for (var name : resources.getXObjectNames()) {
                if (resources.isImageXObject(name)) {
                    images++;
                }
            }
            assertThat(images).isEqualTo(1);
        }
    }

    @Test
    void initialsUseOnlyTheFirstLetterOfEachName() {
        assertThat(ReportVerificationController.initials("Riya D. Sharma")).isEqualTo("R. D. S.");
        assertThat(ReportVerificationController.initials("  asha  ")).isEqualTo("A.");
    }
}
