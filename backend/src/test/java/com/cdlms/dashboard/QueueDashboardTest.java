package com.cdlms.dashboard;

import com.cdlms.auth.CsrfHeaderFilter;
import com.cdlms.support.IntegrationTest;
import com.cdlms.user.Role;
import com.cdlms.user.User;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Phase 03 dashboard widgets: live queue for staff, "your place in the queue" for patients. */
class QueueDashboardTest extends IntegrationTest {

    private Cookie reception;
    private Cookie doctor;
    private Cookie admin;
    private Cookie patient;
    private UUID doctorId;
    private UUID patientId;
    private UUID otherPatientId;

    @BeforeEach
    void setUp() {
        reception = testUsers.loginCookie(testUsers.create(Role.RECEPTIONIST));
        admin = testUsers.loginCookie(testUsers.create(Role.ADMIN));
        User doctorUser = testUsers.create(Role.DOCTOR);
        doctor = testUsers.loginCookie(doctorUser);
        User patientUser = testUsers.create(Role.PATIENT);
        patient = testUsers.loginCookie(patientUser);
        User otherUser = testUsers.create(Role.PATIENT, "other@test.local");
        doctorId = jdbc.queryForObject("SELECT id FROM doctors WHERE user_id = ?", UUID.class, doctorUser.getId());
        patientId = jdbc.queryForObject("SELECT id FROM patients WHERE user_id = ?", UUID.class, patientUser.getId());
        otherPatientId = jdbc.queryForObject("SELECT id FROM patients WHERE user_id = ?", UUID.class, otherUser.getId());
    }

    private void token(UUID forPatient) throws Exception {
        mvc.perform(post("/api/queue/tokens").header(CsrfHeaderFilter.HEADER, "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"patientId\":\"" + forPatient + "\",\"doctorId\":\"" + doctorId + "\"}")
                        .cookie(reception))
                .andExpect(status().isCreated());
    }

    @Test
    void patientSeesTheirPlaceInTheQueue() throws Exception {
        token(otherPatientId);
        token(patientId);

        mvc.perform(get("/api/dashboard/patient").cookie(patient))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.widgets[0].type").value("myQueue"))
                .andExpect(jsonPath("$.widgets[0].data.token").value("T-002"))
                .andExpect(jsonPath("$.widgets[0].data.ahead").value(1));
    }

    @Test
    void patientWithoutATokenGetsNoQueueCard() throws Exception {
        mvc.perform(get("/api/dashboard/patient").cookie(patient))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.widgets[0].type").value("myRecord"));
    }

    @Test
    void staffDashboardsCarryTheLiveQueue() throws Exception {
        token(patientId);

        mvc.perform(get("/api/dashboard/receptionist").cookie(reception))
                .andExpect(jsonPath("$.widgets[?(@.type == 'liveQueue')].data.doctors[0].waiting", hasSize(1)));
        mvc.perform(get("/api/dashboard/doctor").cookie(doctor))
                .andExpect(jsonPath("$.widgets[?(@.type == 'liveQueue')]", hasSize(1)));
        mvc.perform(get("/api/dashboard/admin").cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.widgets[?(@.type == 'liveQueue')]", hasSize(1)))
                .andExpect(jsonPath("$.widgets[?(@.type == 'visitsByStatus')].data.CHECKED_IN").value(1));
    }
}
