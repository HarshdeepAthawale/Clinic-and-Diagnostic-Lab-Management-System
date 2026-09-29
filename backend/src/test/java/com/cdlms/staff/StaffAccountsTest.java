package com.cdlms.staff;

import com.cdlms.auth.CsrfHeaderFilter;
import com.cdlms.support.IntegrationTest;
import com.cdlms.support.TestUsers;
import com.cdlms.user.Role;
import com.cdlms.user.User;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Phase 10 follow-up: the admin creates and switches off staff accounts; people change their own password (ADR-032). */
class StaffAccountsTest extends IntegrationTest {

    private User adminUser;
    private Cookie admin;
    private Cookie doctor;
    private Cookie patient;

    @BeforeEach
    void setUp() {
        adminUser = testUsers.create(Role.ADMIN);
        admin = testUsers.loginCookie(adminUser);
        doctor = testUsers.loginCookie(testUsers.create(Role.DOCTOR));
        patient = testUsers.loginCookie(testUsers.create(Role.PATIENT));
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder builder, String body) {
        return builder.header(CsrfHeaderFilter.HEADER, "1").contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private static String body(String role, String name, String email, String extra) {
        return "{\"role\":\"" + role + "\",\"fullName\":\"" + name + "\",\"email\":\"" + email + "\"" + extra + "}";
    }

    private MvcResult login(String email, String password) throws Exception {
        return mvc.perform(json(post("/api/auth/login"), "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}")).andReturn();
    }

    private String create(String role, String name, String email, String extra) throws Exception {
        MvcResult result = mvc.perform(json(post("/api/admin/staff"), body(role, name, email, extra)).cookie(admin))
                .andExpect(status().isCreated()).andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.temporaryPassword");
    }

    // ---------------------------------------------------------------- creating

    @Test
    void everyStaffRoleCanBeCreatedAndSignsInWithItsTemporaryPassword() throws Exception {
        String receptionPw = create("RECEPTIONIST", "Ravi Front", "ravi.front@test.local", "");
        String labPw = create("LAB_TECHNICIAN", "Lata Lab", "lata.lab@test.local", "");
        String adminPw = create("ADMIN", "Amit Admin", "amit.admin@test.local", "");
        String doctorPw = create("DOCTOR", "Dr. Dev Doctor", "dev.doctor@test.local", ",\"specialization\":\"Cardiology\"");
        String pathPw = create("PATHOLOGIST", "Dr. Priya Path", "priya.path@test.local",
                ",\"qualification\":\"MD Pathology\",\"registrationNumber\":\"MMC-2020-99001\"");

        assertThat(receptionPw).hasSize(12).matches("[A-Za-z0-9]+");
        for (String[] account : new String[][] {{"ravi.front@test.local", receptionPw, "RECEPTIONIST"}, {"lata.lab@test.local", labPw, "LAB_TECHNICIAN"},
                {"amit.admin@test.local", adminPw, "ADMIN"}, {"dev.doctor@test.local", doctorPw, "DOCTOR"}, {"priya.path@test.local", pathPw, "PATHOLOGIST"}}) {
            MvcResult result = login(account[0], account[1]);
            assertThat(result.getResponse().getStatus()).isEqualTo(200);
            assertThat((String) JsonPath.read(result.getResponse().getContentAsString(), "$.role")).isEqualTo(account[2]);
        }
        // Their profile rows exist too, so the account works across the app.
        assertThat(jdbc.queryForObject("SELECT specialization FROM doctors WHERE full_name = 'Dr. Dev Doctor'", String.class)).isEqualTo("Cardiology");
        assertThat(jdbc.queryForObject("SELECT registration_number FROM pathologists WHERE full_name = 'Dr. Priya Path'", String.class)).isEqualTo("MMC-2020-99001");
    }

    @Test
    void thePasswordIsStoredOnlyAsAHashAndNotInTheList() throws Exception {
        String password = create("RECEPTIONIST", "Ravi Front", "ravi.front@test.local", "");
        assertThat(jdbc.queryForObject("SELECT password_hash FROM users WHERE email = 'ravi.front@test.local'", String.class)).isNotEqualTo(password).startsWith("$2");
        String list = mvc.perform(get("/api/admin/staff").cookie(admin)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(list).doesNotContain(password).doesNotContain("passwordHash").doesNotContain("temporaryPassword");
        mvc.perform(json(post("/api/admin/staff"), body("RECEPTIONIST", "Ravi Front", "another@test.local", "")).cookie(admin))
                .andExpect(cookie().doesNotExist("cdlms_token"));
    }

    @Test
    void theRequestMustBeComplete() throws Exception {
        mvc.perform(json(post("/api/admin/staff"), body("DOCTOR", "Dr. No Speciality", "nospec@test.local", "")).cookie(admin))
                .andExpect(status().isBadRequest());
        mvc.perform(json(post("/api/admin/staff"), body("PATHOLOGIST", "Dr. No Reg", "noreg@test.local", ",\"qualification\":\"MD\"")).cookie(admin))
                .andExpect(status().isBadRequest());
        mvc.perform(json(post("/api/admin/staff"), body("PATIENT", "A Patient", "patient.by.admin@test.local", "")).cookie(admin))
                .andExpect(status().isBadRequest());
        mvc.perform(json(post("/api/admin/staff"), body("RECEPTIONIST", "Bad Email", "not-an-email", "")).cookie(admin))
                .andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM users WHERE email IN ('nospec@test.local','noreg@test.local')", Long.class)).isZero();
    }

    @Test
    void emailsAndRegistrationNumbersMustBeUnique() throws Exception {
        create("RECEPTIONIST", "Ravi Front", "ravi.front@test.local", "");
        mvc.perform(json(post("/api/admin/staff"), body("LAB_TECHNICIAN", "Someone Else", "RAVI.FRONT@test.local", "")).cookie(admin))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("EMAIL_TAKEN"));
        create("PATHOLOGIST", "Dr. First", "first.path@test.local", ",\"qualification\":\"MD\",\"registrationNumber\":\"MMC-1\"");
        mvc.perform(json(post("/api/admin/staff"), body("PATHOLOGIST", "Dr. Second", "second.path@test.local",
                        ",\"qualification\":\"MD\",\"registrationNumber\":\"MMC-1\"")).cookie(admin))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("REGISTRATION_TAKEN"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM users WHERE email = 'second.path@test.local'", Long.class)).isZero();
    }

    // ---------------------------------------------------------------- listing and access

    @Test
    void theListHasStaffButNoPatients() throws Exception {
        create("DOCTOR", "Dr. Dev Doctor", "dev.doctor@test.local", ",\"specialization\":\"Cardiology\"");
        mvc.perform(get("/api/admin/staff?q=dev").cookie(admin)).andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1))).andExpect(jsonPath("$[0].fullName").value("Dr. Dev Doctor"))
                .andExpect(jsonPath("$[0].detail").value("Cardiology")).andExpect(jsonPath("$[0].role").value("DOCTOR"));
        mvc.perform(get("/api/admin/staff").cookie(admin)).andExpect(jsonPath("$[?(@.role == 'PATIENT')]", hasSize(0)));
    }

    @Test
    void onlyAnAdminCanManageStaff() throws Exception {
        for (Cookie other : new Cookie[] {doctor, patient}) {
            mvc.perform(get("/api/admin/staff").cookie(other)).andExpect(status().isForbidden());
            mvc.perform(json(post("/api/admin/staff"), body("ADMIN", "Sneaky", "sneaky@test.local", "")).cookie(other)).andExpect(status().isForbidden());
            mvc.perform(json(patch("/api/admin/staff/" + adminUser.getId() + "/active"), "{\"active\":false}").cookie(other)).andExpect(status().isForbidden());
        }
        mvc.perform(get("/api/admin/staff")).andExpect(status().isUnauthorized());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM users WHERE email = 'sneaky@test.local'", Long.class)).isZero();
    }

    // ---------------------------------------------------------------- switching off

    @Test
    void deactivatingLocksTheAccountOutAtOnceAndReactivatingRestoresIt() throws Exception {
        String password = create("RECEPTIONIST", "Ravi Front", "ravi.front@test.local", "");
        MvcResult signedIn = login("ravi.front@test.local", password);
        Cookie ravi = signedIn.getResponse().getCookie("cdlms_token");
        String userId = jdbc.queryForObject("SELECT id FROM users WHERE email = 'ravi.front@test.local'", String.class);
        mvc.perform(get("/api/auth/me").cookie(ravi)).andExpect(status().isOk());

        mvc.perform(json(patch("/api/admin/staff/" + userId + "/active"), "{\"active\":false}").cookie(admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.active").value(false));
        // The cookie they already hold stops working, and signing in again is refused.
        mvc.perform(get("/api/auth/me").cookie(ravi)).andExpect(status().isUnauthorized());
        assertThat(login("ravi.front@test.local", password).getResponse().getStatus()).isEqualTo(403);
        mvc.perform(get("/api/admin/staff?includeInactive=false").cookie(admin)).andExpect(jsonPath("$[?(@.email == 'ravi.front@test.local')]", hasSize(0)));
        mvc.perform(get("/api/admin/staff").cookie(admin)).andExpect(jsonPath("$[?(@.email == 'ravi.front@test.local')].active").value(false));

        mvc.perform(json(patch("/api/admin/staff/" + userId + "/active"), "{\"active\":true}").cookie(admin)).andExpect(status().isOk());
        assertThat(login("ravi.front@test.local", password).getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void anAdminCannotSwitchThemselvesOffAndPatientsAreNotStaff() throws Exception {
        mvc.perform(json(patch("/api/admin/staff/" + adminUser.getId() + "/active"), "{\"active\":false}").cookie(admin))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CANNOT_DEACTIVATE_SELF"));
        String patientUserId = jdbc.queryForObject("SELECT id FROM users WHERE role = 'PATIENT' LIMIT 1", String.class);
        mvc.perform(json(patch("/api/admin/staff/" + patientUserId + "/active"), "{\"active\":false}").cookie(admin)).andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("SELECT is_active FROM users WHERE id = ?::uuid", Boolean.class, patientUserId)).isTrue();
    }

    // ---------------------------------------------------------------- changing your own password

    @Test
    void aNewStaffMemberChangesTheTemporaryPasswordAndTheOldOneStopsWorking() throws Exception {
        String temporary = create("LAB_TECHNICIAN", "Lata Lab", "lata.lab@test.local", "");
        Cookie lata = login("lata.lab@test.local", temporary).getResponse().getCookie("cdlms_token");

        mvc.perform(json(post("/api/auth/change-password"), "{\"currentPassword\":\"" + temporary + "\",\"newPassword\":\"a-much-better-pw-1\"}").cookie(lata))
                .andExpect(status().isNoContent());
        assertThat(login("lata.lab@test.local", temporary).getResponse().getStatus()).isEqualTo(401);
        assertThat(login("lata.lab@test.local", "a-much-better-pw-1").getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void changingAPasswordNeedsTheRightCurrentOneAndAnActuallyNewOne() throws Exception {
        mvc.perform(json(post("/api/auth/change-password"), "{\"currentPassword\":\"wrong\",\"newPassword\":\"a-much-better-pw-1\"}").cookie(doctor))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
        mvc.perform(json(post("/api/auth/change-password"), "{\"currentPassword\":\"" + TestUsers.PASSWORD + "\",\"newPassword\":\"" + TestUsers.PASSWORD + "\"}").cookie(doctor))
                .andExpect(status().isBadRequest());
        mvc.perform(json(post("/api/auth/change-password"), "{\"currentPassword\":\"" + TestUsers.PASSWORD + "\",\"newPassword\":\"short\"}").cookie(doctor))
                .andExpect(status().isBadRequest());
        mvc.perform(json(post("/api/auth/change-password"), "{\"currentPassword\":\"x\",\"newPassword\":\"a-much-better-pw-1\"}")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/me").cookie(doctor)).andExpect(jsonPath("$.role", not("PATIENT")));
    }
}
