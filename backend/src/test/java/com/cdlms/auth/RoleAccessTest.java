package com.cdlms.auth;

import com.cdlms.support.IntegrationTest;
import com.cdlms.user.Role;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Phase 01 exit criterion: each role reaches only its own area; any other role's endpoint is 403. */
class RoleAccessTest extends IntegrationTest {

    private static final Map<Role, String> DASHBOARDS = Map.of(
            Role.PATIENT, "/api/dashboard/patient",
            Role.DOCTOR, "/api/dashboard/doctor",
            Role.PATHOLOGIST, "/api/dashboard/pathologist",
            Role.RECEPTIONIST, "/api/dashboard/receptionist",
            Role.LAB_TECHNICIAN, "/api/dashboard/lab-technician",
            Role.ADMIN, "/api/dashboard/admin");

    @ParameterizedTest
    @EnumSource(Role.class)
    void eachRoleReachesOnlyItsOwnDashboard(Role role) throws Exception {
        Cookie cookie = testUsers.loginCookie(testUsers.create(role));

        for (Map.Entry<Role, String> dashboard : DASHBOARDS.entrySet()) {
            if (dashboard.getKey() == role) {
                mvc.perform(get(dashboard.getValue()).cookie(cookie))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.role").value(role.name()));
            } else {
                mvc.perform(get(dashboard.getValue()).cookie(cookie))
                        .andExpect(status().isForbidden())
                        .andExpect(jsonPath("$.code").value("FORBIDDEN"));
            }
        }
    }

    @ParameterizedTest
    @EnumSource(Role.class)
    void dashboardsRequireLogin(Role role) throws Exception {
        mvc.perform(get(DASHBOARDS.get(role)))
                .andExpect(status().isUnauthorized());
    }
}
