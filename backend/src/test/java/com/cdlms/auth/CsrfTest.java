package com.cdlms.auth;

import com.cdlms.support.IntegrationTest;
import com.cdlms.user.Role;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** ADR-014 / Docs/TestPlan.md "CSRF". */
class CsrfTest extends IntegrationTest {

    @Test
    void mutatingRequestWithoutHeaderIsRejectedEvenOnLogin() throws Exception {
        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"a@b.c\",\"password\":\"x\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_HEADER_MISSING"));
    }

    @Test
    void mutatingRequestWithValidCookieButNoHeaderIsRejected() throws Exception {
        Cookie cookie = testUsers.loginCookie(testUsers.create(Role.PATIENT));

        mvc.perform(post("/api/auth/logout").cookie(cookie))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_HEADER_MISSING"));
    }

    @Test
    void wrongHeaderValueIsRejected() throws Exception {
        mvc.perform(post("/api/auth/logout").header(CsrfHeaderFilter.HEADER, "yes"))
                .andExpect(status().isForbidden());
    }

    @Test
    void getRequestsDoNotNeedTheHeader() throws Exception {
        Cookie cookie = testUsers.loginCookie(testUsers.create(Role.PATIENT));

        mvc.perform(get("/api/auth/me").cookie(cookie)).andExpect(status().isOk());
    }

    @Test
    void crossOriginPreflightIsNotGranted() throws Exception {
        mvc.perform(options("/api/auth/login")
                        .header("Origin", "https://evil.example")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", CsrfHeaderFilter.HEADER))
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Headers"));
    }
}
