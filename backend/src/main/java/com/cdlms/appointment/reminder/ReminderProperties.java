package com.cdlms.appointment.reminder;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Appointment reminder settings ({@code app.reminders.*}).
 *
 * @param enabled      run the scheduled job (off in tests, which call {@link ReminderJob#sendDue()} directly)
 * @param leadTime     send once a booked appointment is this close
 * @param pollInterval how often to look for due reminders
 * @param from         sender address on reminder emails
 * @param clinicName   name used in the email text
 */
@ConfigurationProperties("app.reminders")
public record ReminderProperties(Boolean enabled, Duration leadTime, Duration pollInterval, String from,
                                 String clinicName) {

    public ReminderProperties {
        enabled = enabled == null || enabled;
        leadTime = leadTime == null ? Duration.ofHours(24) : leadTime;
        pollInterval = pollInterval == null ? Duration.ofMinutes(5) : pollInterval;
        from = from == null ? "CDLMS Clinic <no-reply@cdlms.local>" : from;
        clinicName = clinicName == null ? "CDLMS Clinic" : clinicName;
    }
}
