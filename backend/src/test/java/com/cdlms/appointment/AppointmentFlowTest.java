package com.cdlms.appointment;

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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Phase 03: booking, walk-in tokens, status lifecycle, queue board, working hours (Rules.md §1a, ADR-020). */
class AppointmentFlowTest extends IntegrationTest {

    private static final ZoneId CLINIC = ZoneId.of("Asia/Kolkata");

    private Cookie reception;
    private Cookie doctor;
    private Cookie otherDoctor;
    private Cookie patient;
    private UUID doctorId;
    private UUID otherDoctorId;
    private UUID patientId;
    private UUID secondPatientId;

    @BeforeEach
    void setUp() {
        reception = testUsers.loginCookie(testUsers.create(Role.RECEPTIONIST));
        User doctorUser = testUsers.create(Role.DOCTOR);
        doctor = testUsers.loginCookie(doctorUser);
        User otherDoctorUser = testUsers.create(Role.DOCTOR, "other.doctor@test.local");
        otherDoctor = testUsers.loginCookie(otherDoctorUser);
        User patientUser = testUsers.create(Role.PATIENT);
        patient = testUsers.loginCookie(patientUser);
        User secondPatientUser = testUsers.create(Role.PATIENT, "second.patient@test.local");

        doctorId = jdbc.queryForObject("SELECT id FROM doctors WHERE user_id = ?", UUID.class, doctorUser.getId());
        otherDoctorId = jdbc.queryForObject("SELECT id FROM doctors WHERE user_id = ?", UUID.class, otherDoctorUser.getId());
        patientId = jdbc.queryForObject("SELECT id FROM patients WHERE user_id = ?", UUID.class, patientUser.getId());
        secondPatientId = jdbc.queryForObject("SELECT id FROM patients WHERE user_id = ?", UUID.class,
                secondPatientUser.getId());

        // Open every day, 08:00–20:00 in 15-minute slots, so tests don't depend on the weekday.
        for (int day = 1; day <= 7; day++) {
            jdbc.update("INSERT INTO doctor_schedules (doctor_id, day_of_week, start_time, end_time, slot_minutes) "
                    + "VALUES (?, ?, '08:00', '20:00', 15)", doctorId, day);
        }
    }

    // ---------------------------------------------------------------- helpers

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder builder, String body) {
        return builder.header(CsrfHeaderFilter.HEADER, "1").contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private static Instant tomorrowAt(int hour, int minute) {
        return LocalDate.now(CLINIC).plusDays(1).atTime(LocalTime.of(hour, minute)).atZone(CLINIC).toInstant();
    }

    private ResultActions book(Cookie who, Instant slot, UUID forPatient) throws Exception {
        String patientField = forPatient == null ? "" : ",\"patientId\":\"" + forPatient + "\"";
        return mvc.perform(json(post("/api/appointments"),
                "{\"doctorId\":\"" + doctorId + "\",\"scheduledAt\":\"" + slot + "\"" + patientField
                        + ",\"reason\":\"Fever for 3 days\"}").cookie(who));
    }

    private ResultActions setStatus(Cookie who, String appointmentId, String status) throws Exception {
        return mvc.perform(json(patch("/api/appointments/" + appointmentId + "/status"),
                "{\"status\":\"" + status + "\"}").cookie(who));
    }

    private ResultActions issueToken(UUID forPatient, UUID withDoctor) throws Exception {
        return mvc.perform(json(post("/api/queue/tokens"),
                "{\"patientId\":\"" + forPatient + "\",\"doctorId\":\"" + withDoctor + "\"}").cookie(reception));
    }

    /** A booking for right now, inserted directly (booking through the API needs a future slot). */
    private String bookedNow(UUID forPatient) {
        return jdbc.queryForObject("INSERT INTO appointments (patient_id, doctor_id, scheduled_at, status) "
                + "VALUES (?, ?, ?, 'BOOKED') RETURNING id", UUID.class, forPatient, doctorId,
                Timestamp.from(Instant.now())).toString();
    }

    private static String id(MvcResult result) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    // ---------------------------------------------------------------- booking

    @Test
    void patientBooksAFreeSlotForThemself() throws Exception {
        book(patient, tomorrowAt(10, 30), null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("BOOKED"))
                .andExpect(jsonPath("$.kind").value("SCHEDULED"))
                .andExpect(jsonPath("$.patient.id").value(patientId.toString()))
                .andExpect(jsonPath("$.durationMinutes").value(15))
                .andExpect(jsonPath("$.queueToken").doesNotExist());
    }

    @Test
    void aSlotCanOnlyBeBookedOnce() throws Exception {
        book(patient, tomorrowAt(11, 0), null).andExpect(status().isCreated());
        book(reception, tomorrowAt(11, 0), secondPatientId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SLOT_TAKEN"));
    }

    @Test
    void cancellingFreesTheSlot() throws Exception {
        String id = id(book(patient, tomorrowAt(11, 15), null).andReturn());
        setStatus(patient, id, "CANCELLED").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));
        book(reception, tomorrowAt(11, 15), secondPatientId).andExpect(status().isCreated());
    }

    @Test
    void bookingMustMatchTheDoctorsHours() throws Exception {
        book(patient, tomorrowAt(10, 7), null).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("NOT_A_SLOT"));
        book(patient, tomorrowAt(21, 0), null).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("NOT_A_SLOT"));
        book(patient, Instant.now().minusSeconds(3600), null).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SLOT_IN_PAST"));
    }

    @Test
    void onePatientOneBookingPerDoctorPerDay() throws Exception {
        book(patient, tomorrowAt(9, 0), null).andExpect(status().isCreated());
        book(patient, tomorrowAt(15, 0), null).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_BOOKED"));
    }

    @Test
    void frontDeskMustNameThePatient() throws Exception {
        book(reception, tomorrowAt(9, 30), null).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PATIENT_REQUIRED"));
    }

    @Test
    void slotPickerMarksTakenSlots() throws Exception {
        book(patient, tomorrowAt(8, 0), null).andExpect(status().isCreated());
        mvc.perform(get("/api/doctors/" + doctorId + "/slots?date=" + LocalDate.now(CLINIC).plusDays(1)).cookie(reception))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.working").value(true))
                .andExpect(jsonPath("$.slots", hasSize(48)))
                .andExpect(jsonPath("$.slots[0].available").value(false))
                .andExpect(jsonPath("$.slots[1].available").value(true));
    }

    @Test
    void doctorsWithoutHoursHaveNoSlots() throws Exception {
        mvc.perform(get("/api/doctors/" + otherDoctorId + "/slots?date=" + LocalDate.now(CLINIC)).cookie(patient))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.working").value(false))
                .andExpect(jsonPath("$.slots", hasSize(0)));
    }

    @Test
    void badDateIsA400NotA500() throws Exception {
        mvc.perform(get("/api/doctors/" + doctorId + "/slots?date=tomorrow").cookie(patient))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
    }

    // ---------------------------------------------------------------- walk-in tokens

    @Test
    void walkInTokensAreNumberedPerDayAndJoinTheQueue() throws Exception {
        issueToken(patientId, doctorId)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.kind").value("WALK_IN"))
                .andExpect(jsonPath("$.status").value("CHECKED_IN"))
                .andExpect(jsonPath("$.queueToken").value("T-001"));
        issueToken(secondPatientId, otherDoctorId).andExpect(jsonPath("$.queueToken").value("T-002"));

        mvc.perform(get("/api/queue").cookie(reception))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.doctors", hasSize(2)))
                .andExpect(jsonPath("$.doctors[0].waiting", hasSize(1)));
    }

    @Test
    void walkInCreatesTheCareRelationship() throws Exception {
        mvc.perform(get("/api/patients/" + patientId + "/history").cookie(doctor)).andExpect(status().isForbidden());
        issueToken(patientId, doctorId).andExpect(status().isCreated());
        mvc.perform(get("/api/patients/" + patientId + "/history").cookie(doctor)).andExpect(status().isOk());
    }

    @Test
    void onlyTheFrontDeskIssuesTokens() throws Exception {
        mvc.perform(json(post("/api/queue/tokens"),
                        "{\"patientId\":\"" + patientId + "\",\"doctorId\":\"" + doctorId + "\"}").cookie(doctor))
                .andExpect(status().isForbidden());
    }

    // ---------------------------------------------------------------- lifecycle

    @Test
    void fullVisitLifecycleIsRecorded() throws Exception {
        String id = bookedNow(patientId);

        setStatus(reception, id, "CHECKED_IN").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CHECKED_IN"))
                .andExpect(jsonPath("$.queueToken").value("T-001"))
                .andExpect(jsonPath("$.checkedInAt").exists());
        setStatus(doctor, id, "IN_CONSULTATION").andExpect(status().isOk())
                .andExpect(jsonPath("$.startedAt").exists());
        setStatus(doctor, id, "COMPLETED").andExpect(status().isOk())
                .andExpect(jsonPath("$.completedAt").exists());

        mvc.perform(get("/api/appointments/" + id).cookie(reception))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.history", hasSize(3)))
                .andExpect(jsonPath("$.history[0].fromStatus").value("BOOKED"))
                .andExpect(jsonPath("$.history[2].toStatus").value("COMPLETED"));
    }

    @Test
    void onlyAllowedMovesAndRoles() throws Exception {
        String id = bookedNow(patientId);
        setStatus(patient, id, "CHECKED_IN").andExpect(status().isForbidden());
        setStatus(doctor, id, "COMPLETED").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_TRANSITION"));
    }

    @Test
    void doctorSeesOnePatientAtATime() throws Exception {
        issueToken(patientId, doctorId);
        issueToken(secondPatientId, doctorId);
        String first = jdbc.queryForObject("SELECT id FROM appointments WHERE queue_number = 1", UUID.class).toString();
        String second = jdbc.queryForObject("SELECT id FROM appointments WHERE queue_number = 2", UUID.class).toString();

        setStatus(doctor, first, "IN_CONSULTATION").andExpect(status().isOk());
        setStatus(doctor, second, "IN_CONSULTATION").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_IN_CONSULTATION"));
    }

    @Test
    void checkInOnlyOnTheDay() throws Exception {
        String id = id(book(patient, tomorrowAt(12, 0), null).andReturn());
        setStatus(reception, id, "CHECKED_IN").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NOT_TODAY"));
    }

    @Test
    void noShowOnlyAfterTheAppointmentTime() throws Exception {
        String id = id(book(patient, tomorrowAt(12, 30), null).andReturn());
        setStatus(reception, id, "NO_SHOW").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TOO_EARLY"));
    }

    @Test
    void doctorsOnlySeeTheirOwnAppointments() throws Exception {
        String id = bookedNow(patientId);
        mvc.perform(get("/api/appointments/" + id).cookie(otherDoctor)).andExpect(status().isNotFound());
        setStatus(otherDoctor, id, "NO_SHOW").andExpect(status().isNotFound());
        mvc.perform(get("/api/appointments").cookie(otherDoctor)).andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(get("/api/appointments").cookie(doctor)).andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void patientsOnlySeeTheirOwnAppointments() throws Exception {
        String theirs = bookedNow(secondPatientId);
        mvc.perform(get("/api/appointments/" + theirs).cookie(patient)).andExpect(status().isNotFound());
        setStatus(patient, theirs, "CANCELLED").andExpect(status().isNotFound());

        book(patient, tomorrowAt(16, 0), null);
        mvc.perform(get("/api/appointments/mine").cookie(patient))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.upcoming", hasSize(1)))
                .andExpect(jsonPath("$.past", hasSize(0)));
    }

    @Test
    void appointmentHistoryIsAppendOnly() throws Exception {
        String id = id(book(patient, tomorrowAt(17, 0), null).andReturn());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM appointment_events WHERE appointment_id = ?", Long.class,
                UUID.fromString(id))).isEqualTo(1);
        assertThatThrownBy(() -> jdbc.update("UPDATE appointment_events SET note = 'edited'"))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("DELETE FROM appointment_events"))
                .isInstanceOf(DataAccessException.class);
    }

    // ---------------------------------------------------------------- working hours

    @Test
    void doctorSetsOwnWorkingHoursOnly() throws Exception {
        String hours = """
                {"blocks":[{"dayOfWeek":1,"startTime":"09:00","endTime":"13:00","slotMinutes":20},
                           {"dayOfWeek":1,"startTime":"16:00","endTime":"18:00","slotMinutes":20}]}""";
        mvc.perform(json(put("/api/doctors/" + otherDoctorId + "/working-hours"), hours).cookie(otherDoctor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
        mvc.perform(json(put("/api/doctors/" + doctorId + "/working-hours"), hours).cookie(otherDoctor))
                .andExpect(status().isForbidden());
        mvc.perform(json(put("/api/doctors/" + otherDoctorId + "/working-hours"), hours).cookie(reception))
                .andExpect(status().isForbidden());
    }

    @Test
    void overlappingHoursAreRejected() throws Exception {
        mvc.perform(json(put("/api/doctors/" + doctorId + "/working-hours"), """
                        {"blocks":[{"dayOfWeek":2,"startTime":"09:00","endTime":"13:00","slotMinutes":15},
                                   {"dayOfWeek":2,"startTime":"12:00","endTime":"14:00","slotMinutes":15}]}""")
                        .cookie(doctor))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("OVERLAPPING_HOURS"));
    }
}
