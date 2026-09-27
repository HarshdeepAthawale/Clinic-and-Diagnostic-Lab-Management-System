package com.cdlms.appointment.reminder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Used when no mail server is configured: the reminder is logged (without the email address) instead of sent. */
public class LoggingReminderSender implements ReminderSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingReminderSender.class);

    @Override
    public void send(Reminder r) {
        log.info("Mail not configured; reminder for appointment {} ({} with {} at {}) not emailed",
                r.appointmentId(), r.patientCode(), r.doctorName(), r.scheduledAt());
    }
}
