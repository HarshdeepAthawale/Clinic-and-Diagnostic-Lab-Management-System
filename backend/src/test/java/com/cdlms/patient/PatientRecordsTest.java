package com.cdlms.patient;

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

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Phase 02: registration, registration codes, search, care relationship, access log (ADR-015, ADR-018). */
class PatientRecordsTest extends IntegrationTest {

    private static final String NEW_PATIENT = """
            {"fullName":"Karan Malhotra","dob":"2001-03-14","gender":"MALE","phone":"+91 98200 11223",
             "address":"4 Ring Road","knownAllergies":"Sulfa drugs","bloodGroup":"B+",
             "emergencyContactName":"Anita Malhotra","emergencyContactPhone":"+91 98200 44556"}""";

    private Cookie reception;
    private Cookie doctor;
    private User doctorUser;

    @BeforeEach
    void setUp() {
        reception = testUsers.loginCookie(testUsers.create(Role.RECEPTIONIST));
        doctorUser = testUsers.create(Role.DOCTOR);
        doctor = testUsers.loginCookie(doctorUser);
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder builder, String body) {
        return builder.header(CsrfHeaderFilter.HEADER, "1").contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private MvcResult register() throws Exception {
        return mvc.perform(json(post("/api/patients"), NEW_PATIENT).cookie(reception))
                .andExpect(status().isCreated())
                .andReturn();
    }

    private static String read(MvcResult result, String path) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(), path);
    }

    private void book(String patientId, String status) {
        UUID doctorId = jdbc.queryForObject("SELECT id FROM doctors WHERE user_id = ?", UUID.class, doctorUser.getId());
        jdbc.update("INSERT INTO appointments (patient_id, doctor_id, scheduled_at, status) VALUES (?, ?, ?, ?)",
                UUID.fromString(patientId), doctorId, Timestamp.from(Instant.now()), status);
    }

    private long accessLogRows(String patientId) {
        return jdbc.queryForObject("SELECT count(*) FROM patient_access_log WHERE patient_id = ?", Long.class,
                UUID.fromString(patientId));
    }

    // ---------------------------------------------------------------- registration

    @Test
    void receptionistRegistersPatientAndGetsOneTimeCode() throws Exception {
        MvcResult result = register();

        assertThat(read(result, "$.patient.patientCode")).matches("PID-\\d{6}");
        assertThat(read(result, "$.registrationCode")).matches("[A-Z2-9]{5}-[A-Z2-9]{5}");
        String storedHash = jdbc.queryForObject("SELECT claim_code_hash FROM patients", String.class);
        assertThat(storedHash).hasSize(64).doesNotContain(read(result, "$.registrationCode"));
    }

    @Test
    void onlyReceptionCanRegister() throws Exception {
        mvc.perform(json(post("/api/patients"), NEW_PATIENT).cookie(doctor)).andExpect(status().isForbidden());
    }

    @Test
    void patientLinksLoginWithCodeAndSeesTheSameRecord() throws Exception {
        MvcResult registered = register();
        String code = read(registered, "$.registrationCode").toLowerCase().replace("-", " ");

        MvcResult claimed = mvc.perform(json(post("/api/auth/register/claim"),
                        "{\"email\":\"karan@test.local\",\"password\":\"a-good-password\",\"registrationCode\":\"" + code + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("PATIENT"))
                .andExpect(jsonPath("$.name").value("Karan Malhotra"))
                .andReturn();
        Cookie patient = claimed.getResponse().getCookie("cdlms_token");

        mvc.perform(get("/api/patients/me").cookie(patient))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.patientCode").value(read(registered, "$.patient.patientCode")))
                .andExpect(jsonPath("$.knownAllergies").value("Sulfa drugs"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM patients", Long.class)).isEqualTo(1);
    }

    @Test
    void registrationCodeWorksOnlyOnce() throws Exception {
        String code = read(register(), "$.registrationCode");
        String body = "{\"email\":\"%s\",\"password\":\"a-good-password\",\"registrationCode\":\"" + code + "\"}";

        mvc.perform(json(post("/api/auth/register/claim"), body.formatted("one@test.local"))).andExpect(status().isCreated());
        mvc.perform(json(post("/api/auth/register/claim"), body.formatted("two@test.local")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REGISTRATION_CODE"));
    }

    @Test
    void expiredOrUnknownCodesAreRejectedTheSameWay() throws Exception {
        String code = read(register(), "$.registrationCode");
        jdbc.update("UPDATE patients SET claim_code_expires_at = now() - interval '1 day'");
        String body = "{\"email\":\"late@test.local\",\"password\":\"a-good-password\",\"registrationCode\":\"%s\"}";

        mvc.perform(json(post("/api/auth/register/claim"), body.formatted(code)))
                .andExpect(jsonPath("$.code").value("INVALID_REGISTRATION_CODE"));
        mvc.perform(json(post("/api/auth/register/claim"), body.formatted("AAAAA-BBBBB")))
                .andExpect(jsonPath("$.code").value("INVALID_REGISTRATION_CODE"));
    }

    // ---------------------------------------------------------------- search

    @Test
    void doctorSearchReturnsSummaryWithMaskedPhoneAndCareFlag() throws Exception {
        String id = read(register(), "$.patient.id");

        mvc.perform(get("/api/patients").param("q", "karan").cookie(doctor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].maskedPhone").value("91********23"))
                .andExpect(jsonPath("$.content[0].hasCareRelationship").value(false))
                .andExpect(jsonPath("$.content[0].knownAllergies").doesNotExist())
                .andExpect(jsonPath("$.content[0].phone").doesNotExist());

        book(id, "BOOKED");
        mvc.perform(get("/api/patients").param("q", "98200 11").cookie(doctor))
                .andExpect(jsonPath("$.content[0].hasCareRelationship").value(true));
    }

    @Test
    void searchMatchesPatientCodeAndHidesCareFlagFromReception() throws Exception {
        String code = read(register(), "$.patient.patientCode");

        mvc.perform(get("/api/patients").param("q", code.toLowerCase()).cookie(reception))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].hasCareRelationship").doesNotExist());
    }

    // ---------------------------------------------------------------- care relationship + access log

    @Test
    void doctorWithoutAppointmentCannotOpenRecordAndNothingIsLogged() throws Exception {
        String id = read(register(), "$.patient.id");

        mvc.perform(get("/api/patients/{id}/history", id).cookie(doctor))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NO_CARE_RELATIONSHIP"));
        assertThat(accessLogRows(id)).isZero();
    }

    @Test
    void cancelledAppointmentAloneGrantsNoAccess() throws Exception {
        String id = read(register(), "$.patient.id");
        book(id, "CANCELLED");

        mvc.perform(get("/api/patients/{id}/history", id).cookie(doctor))
                .andExpect(jsonPath("$.code").value("NO_CARE_RELATIONSHIP"));
    }

    @Test
    void doctorWithAppointmentOpensRecordAndEachReadIsLogged() throws Exception {
        String id = read(register(), "$.patient.id");
        book(id, "BOOKED");

        mvc.perform(get("/api/patients/{id}/history", id).cookie(doctor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.knownAllergies").value("Sulfa drugs"))
                .andExpect(jsonPath("$.bloodGroup").value("B+"));
        mvc.perform(get("/api/patients/{id}/history", id).cookie(doctor)).andExpect(status().isOk());

        assertThat(accessLogRows(id)).isEqualTo(2);
    }

    @Test
    void doctorUpdatesClinicalFieldsOnlyWithCareRelationship() throws Exception {
        String id = read(register(), "$.patient.id");
        String update = "{\"knownAllergies\":\"Sulfa drugs, Penicillin\",\"medicalHistory\":\"Asthma since 2010\",\"bloodGroup\":\"B+\"}";

        mvc.perform(json(patch("/api/patients/{id}/clinical", id), update).cookie(doctor))
                .andExpect(jsonPath("$.code").value("NO_CARE_RELATIONSHIP"));

        book(id, "BOOKED");
        mvc.perform(json(patch("/api/patients/{id}/clinical", id), update).cookie(doctor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.medicalHistory").value("Asthma since 2010"));
    }

    @Test
    void receptionSeesDemographicsButNotTheMedicalRecord() throws Exception {
        String id = read(register(), "$.patient.id");

        mvc.perform(get("/api/patients/{id}", id).cookie(reception))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phone").value("+91 98200 11223"))
                .andExpect(jsonPath("$.knownAllergies").doesNotExist());
        mvc.perform(get("/api/patients/{id}/history", id).cookie(reception)).andExpect(status().isForbidden());
    }

    @Test
    void patientCannotSeeAnotherPatient() throws Exception {
        String otherId = read(register(), "$.patient.id");
        Cookie patient = testUsers.loginCookie(testUsers.create(Role.PATIENT));

        mvc.perform(get("/api/patients/{id}", otherId).cookie(patient)).andExpect(status().isForbidden());
        mvc.perform(get("/api/patients/{id}/history", otherId).cookie(patient)).andExpect(status().isForbidden());
    }

    @Test
    void adminSeesAccessLogAndNobodyCanRewriteIt() throws Exception {
        String id = read(register(), "$.patient.id");
        book(id, "BOOKED");
        mvc.perform(get("/api/patients/{id}/history", id).cookie(doctor)).andExpect(status().isOk());
        Cookie admin = testUsers.loginCookie(testUsers.create(Role.ADMIN));

        mvc.perform(get("/api/admin/access-log").param("patientId", id).cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].userName").value("Test DOCTOR"))
                .andExpect(jsonPath("$.content[0].patientName").value("Karan Malhotra"));
        mvc.perform(get("/api/admin/access-log").cookie(doctor)).andExpect(status().isForbidden());

        assertThatThrownBy(() -> jdbc.update("DELETE FROM patient_access_log")).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE patient_access_log SET resource = 'EMR'")).isInstanceOf(DataAccessException.class);
    }

    // ---------------------------------------------------------------- dashboards (real data)

    @Test
    void dashboardsReportRealCounts() throws Exception {
        String id = read(register(), "$.patient.id");
        book(id, "BOOKED");

        mvc.perform(get("/api/dashboard/receptionist").cookie(reception))
                .andExpect(jsonPath("$.widgets[0].type").value("stats"))
                .andExpect(jsonPath("$.widgets[0].data[?(@.key=='registeredToday')].value").value(1))
                .andExpect(jsonPath("$.widgets[0].data[?(@.key=='pendingCodes')].value").value(1));
        mvc.perform(get("/api/dashboard/doctor").cookie(doctor))
                .andExpect(jsonPath("$.widgets[0].data[?(@.key=='underCare')].value").value(1))
                .andExpect(jsonPath("$.widgets[?(@.type=='schedule')].data[0].patient.fullName").value("Karan Malhotra"));
    }
}
