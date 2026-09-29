package com.cdlms.auth;

import com.cdlms.support.IntegrationTest;
import com.cdlms.support.TestUsers;
import com.cdlms.user.Role;
import com.cdlms.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Repeated wrong passwords are refused with 429 while a real user elsewhere can still sign in (ADR-030). */
class LoginRateLimitTest extends IntegrationTest {

    private MockHttpServletRequestBuilder login(String email, String password, String address) {
        return post("/api/auth/login").header(CsrfHeaderFilter.HEADER, "1").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}")
                .with(request -> {
                    request.setRemoteAddr(address);
                    return request;
                });
    }

    @Test
    void afterFiveWrongPasswordsEvenTheRightOneIsRefusedForAWhile() throws Exception {
        User user = testUsers.create(Role.DOCTOR, "limited.doctor@test.local");
        String address = "203.0.113.10";

        for (int i = 0; i < 5; i++) {
            mvc.perform(login(user.getEmail(), "wrong-password-" + i, address))
                    .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
        }
        mvc.perform(login(user.getEmail(), TestUsers.PASSWORD, address))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("TOO_MANY_ATTEMPTS"))
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("minute")));
    }

    @Test
    void theRealUserFromAnotherAddressIsNotAffected() throws Exception {
        User user = testUsers.create(Role.DOCTOR, "targeted.doctor@test.local");
        for (int i = 0; i < 5; i++) {
            mvc.perform(login(user.getEmail(), "guess-" + i, "203.0.113.20")).andExpect(status().isUnauthorized());
        }
        mvc.perform(login(user.getEmail(), "guess-again", "203.0.113.20")).andExpect(status().isTooManyRequests());

        mvc.perform(login(user.getEmail(), TestUsers.PASSWORD, "198.51.100.7")).andExpect(status().isOk());
    }

    @Test
    void aCorrectSignInResetsTheCount() throws Exception {
        User user = testUsers.create(Role.DOCTOR, "forgetful.doctor@test.local");
        String address = "203.0.113.30";
        for (int round = 0; round < 3; round++) {
            for (int i = 0; i < 4; i++) {
                mvc.perform(login(user.getEmail(), "typo-" + i, address)).andExpect(status().isUnauthorized());
            }
            mvc.perform(login(user.getEmail(), TestUsers.PASSWORD, address)).andExpect(status().isOk());
        }
    }

    @Test
    void aWrongRegistrationCodeIsStillAnOrdinaryFailureBelowTheCap() throws Exception {
        // The claim cap itself is covered in AttemptLimiterTest; the test profile lifts the per-address cap so
        // that other classes sharing this client address never trip it.
        mvc.perform(post("/api/auth/register/claim").header(CsrfHeaderFilter.HEADER, "1").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"claim.guess@test.local\",\"password\":\"long-enough-pw\",\"registrationCode\":\"NOTAREALCODE\"}")
                        .with(request -> {
                            request.setRemoteAddr("203.0.113.40");
                            return request;
                        }))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REGISTRATION_CODE"));
    }
}
