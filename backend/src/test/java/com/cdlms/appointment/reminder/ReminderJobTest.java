package com.cdlms.appointment.reminder;

import com.cdlms.support.IntegrationTest;
import com.cdlms.user.Role;
import com.cdlms.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/** Phase 03 exit criterion: a reminder email fires before a booked appointment, exactly once. */
class ReminderJobTest extends IntegrationTest {

    @MockitoBean
    private ReminderSender sender;

    @Autowired
    private ReminderJob job;

    private UUID doctorId;
    private UUID patientWithLogin;

    @BeforeEach
    void setUp() {
        reset(sender);
        User doctorUser = testUsers.create(Role.DOCTOR);
        User patientUser = testUsers.create(Role.PATIENT);
        doctorId = jdbc.queryForObject("SELECT id FROM doctors WHERE user_id = ?", UUID.class, doctorUser.getId());
        patientWithLogin = jdbc.queryForObject("SELECT id FROM patients WHERE user_id = ?", UUID.class, patientUser.getId());
    }

    private UUID appointment(UUID patientId, Duration fromNow, String status) {
        return jdbc.queryForObject("INSERT INTO appointments (patient_id, doctor_id, scheduled_at, status) "
                        + "VALUES (?, ?, ?, ?) RETURNING id", UUID.class, patientId, doctorId,
                Timestamp.from(Instant.now().plus(fromNow)), status);
    }

    private Instant reminderSentAt(UUID appointmentId) {
        Timestamp ts = jdbc.queryForObject("SELECT reminder_sent_at FROM appointments WHERE id = ?", Timestamp.class,
                appointmentId);
        return ts == null ? null : ts.toInstant();
    }

    @Test
    void sendsOnceWhenTheAppointmentIsWithinADay() {
        UUID id = appointment(patientWithLogin, Duration.ofHours(3), "BOOKED");

        assertThat(job.sendDue()).isEqualTo(1);
        ArgumentCaptor<Reminder> sent = ArgumentCaptor.forClass(Reminder.class);
        verify(sender).send(sent.capture());
        assertThat(sent.getValue().appointmentId()).isEqualTo(id);
        assertThat(sent.getValue().email()).isEqualTo("patient@test.local");
        assertThat(reminderSentAt(id)).isNotNull();

        assertThat(job.sendDue()).isZero();
        verify(sender, times(1)).send(any());
    }

    @Test
    void skipsAppointmentsThatAreFarOffCancelledOrPast() {
        appointment(patientWithLogin, Duration.ofDays(3), "BOOKED");
        appointment(patientWithLogin, Duration.ofHours(2), "CANCELLED");
        appointment(patientWithLogin, Duration.ofHours(-2), "BOOKED");

        assertThat(job.sendDue()).isZero();
        verify(sender, never()).send(any());
    }

    @Test
    void skipsPatientsWithoutALogin() {
        UUID walkInOnly = jdbc.queryForObject("INSERT INTO patients (full_name, dob, gender, phone) "
                + "VALUES ('No Login', '1980-01-01', 'MALE', '+919999999999') RETURNING id", UUID.class);
        appointment(walkInOnly, Duration.ofHours(1), "BOOKED");

        assertThat(job.sendDue()).isZero();
        verify(sender, never()).send(any());
    }

    @Test
    void failedSendIsRetriedOnTheNextRun() {
        UUID id = appointment(patientWithLogin, Duration.ofHours(1), "BOOKED");
        doThrow(new IllegalStateException("SMTP down")).when(sender).send(any());

        assertThat(job.sendDue()).isZero();
        assertThat(reminderSentAt(id)).isNull();

        reset(sender);
        assertThat(job.sendDue()).isEqualTo(1);
        assertThat(reminderSentAt(id)).isNotNull();
    }

    @Test
    void emailTextNamesDoctorTimeAndPatientId() {
        Instant at = Instant.parse("2026-10-05T04:30:00Z"); // 10:00 in Kolkata
        String body = MailReminderSender.body(new Reminder(UUID.randomUUID(), "a@b.c", "Asha Rao", "PID-000012",
                "Dr. Kabir Mehta", "General Medicine", at), "CDLMS Clinic", ZoneId.of("Asia/Kolkata"));

        assertThat(body).contains("Hello Asha Rao", "Dr. Kabir Mehta (General Medicine)",
                "Monday 5 October 2026 at 10:00 AM", "PID-000012");
    }
}
