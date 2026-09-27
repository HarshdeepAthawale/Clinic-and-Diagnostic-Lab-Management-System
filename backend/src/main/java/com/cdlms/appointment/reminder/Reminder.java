package com.cdlms.appointment.reminder;

import java.time.Instant;
import java.util.UUID;

/** Everything a reminder message needs about one booked appointment. */
public record Reminder(UUID appointmentId, String email, String patientName, String patientCode, String doctorName,
                       String specialization, Instant scheduledAt) {
}
