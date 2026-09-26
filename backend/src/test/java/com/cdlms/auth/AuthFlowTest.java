package com.cdlms.auth;

import com.cdlms.support.IntegrationTest;
import com.cdlms.support.TestUsers;
import com.cdlms.user.Role;
import com.cdlms.user.User;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthFlowTest extends IntegrationTest {

    private static final String REGISTER_BODY = """
            {"email":"New.Patient@Example.com","password":"a-good-password","fullName":"Asha Rao",
             "dob":"1994-05-12","gender":"FEMALE","phone":"+91 98765 43210"}""";

    private static MockHttpServletRequestBuilder mutating(MockHttpServletRequestBuilder builder) {
        return builder.header(CsrfHeaderFilter.HEADER, "1").contentType(MediaType.APPLICATION_JSON);
    }

    private static String login(String email, String password) {
        return "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";
    }

    @Test
    void registerCreatesPatientAndLogsIn() throws Exception {
        MvcResult result = mvc.perform(mutating(post("/api/auth/register")).content(REGISTER_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("new.patient@example.com"))
                .andExpect(jsonPath("$.role").value("PATIENT"))
                .andExpect(jsonPath("$.name").value("Asha Rao"))
                .andReturn();

        String setCookie = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        assertThat(setCookie).contains("cdlms_token=", "HttpOnly", "Secure", "SameSite=Lax", "Path=/");

        Cookie cookie = result.getResponse().getCookie("cdlms_token");
        mvc.perform(get("/api/auth/me").cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Asha Rao"));
        mvc.perform(get("/api/dashboard/patient").cookie(cookie)).andExpect(status().isOk());
    }

    @Test
    void registerRejectsDuplicateEmailCaseInsensitively() throws Exception {
        testUsers.create(Role.PATIENT, "new.patient@example.com");

        mvc.perform(mutating(post("/api/auth/register")).content(REGISTER_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_TAKEN"));
    }

    @Test
    void registerValidatesFields() throws Exception {
        String body = """
                {"email":"not-an-email","password":"short","fullName":"",
                 "dob":"2999-01-01","gender":"FEMALE","phone":"abc"}""";

        mvc.perform(mutating(post("/api/auth/register")).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fields.email").exists())
                .andExpect(jsonPath("$.fields.password").exists())
                .andExpect(jsonPath("$.fields.fullName").exists())
                .andExpect(jsonPath("$.fields.dob").exists())
                .andExpect(jsonPath("$.fields.phone").exists());
    }

    @Test
    void loginReturnsUserAndCookie() throws Exception {
        testUsers.create(Role.DOCTOR, "doc@test.local");

        MvcResult result = mvc.perform(mutating(post("/api/auth/login")).content(login("DOC@test.local", TestUsers.PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("DOCTOR"))
                .andExpect(jsonPath("$.name").value("Test DOCTOR"))
                .andReturn();

        assertThat(result.getResponse().getCookie("cdlms_token")).isNotNull();
    }

    @Test
    void loginWithWrongPasswordOrUnknownEmailGivesSameError() throws Exception {
        testUsers.create(Role.DOCTOR, "doc@test.local");

        mvc.perform(mutating(post("/api/auth/login")).content(login("doc@test.local", "wrong-password")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
        mvc.perform(mutating(post("/api/auth/login")).content(login("nobody@test.local", "wrong-password")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void deactivatedAccountCannotLogInAndExistingTokenStopsWorking() throws Exception {
        User user = testUsers.create(Role.RECEPTIONIST);
        Cookie cookie = testUsers.loginCookie(user);
        testUsers.deactivate(user);

        mvc.perform(mutating(post("/api/auth/login")).content(login(user.getEmail(), TestUsers.PASSWORD)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCOUNT_DISABLED"));
        mvc.perform(get("/api/auth/me").cookie(cookie))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void meWithoutOrWithTamperedCookieIsUnauthenticated() throws Exception {
        mvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        Cookie real = testUsers.loginCookie(testUsers.create(Role.PATIENT));
        String[] parts = real.getValue().split("\\.");
        String forged = parts[0] + "." + parts[1] + "x." + parts[2];
        mvc.perform(get("/api/auth/me").cookie(new Cookie("cdlms_token", forged)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutClearsCookie() throws Exception {
        MvcResult result = mvc.perform(mutating(post("/api/auth/logout")))
                .andExpect(status().isNoContent())
                .andReturn();

        assertThat(result.getResponse().getHeader(HttpHeaders.SET_COOKIE)).contains("cdlms_token=", "Max-Age=0");
    }
}
