package com.cdlms.appointment.reminder;

import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Plain-text reminder email through the configured SMTP server ({@code spring.mail.*}). */
public class MailReminderSender implements ReminderSender {

    private static final DateTimeFormatter WHEN =
            DateTimeFormatter.ofPattern("EEEE d MMMM yyyy 'at' h:mm a", Locale.ENGLISH);

    private final JavaMailSender mail;
    private final ReminderProperties properties;
    private final ZoneId zone;

    public MailReminderSender(JavaMailSender mail, ReminderProperties properties, ZoneId zone) {
        this.mail = mail;
        this.properties = properties;
        this.zone = zone;
    }

    @Override
    public void send(Reminder r) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(properties.from());
        message.setTo(r.email());
        message.setSubject("Reminder: your appointment with " + r.doctorName());
        message.setText(body(r, properties.clinicName(), zone));
        mail.send(message);
    }

    static String body(Reminder r, String clinicName, ZoneId zone) {
        return """
                Hello %s,

                This is a reminder of your appointment at %s:

                  Doctor:      %s (%s)
                  When:        %s
                  Patient ID:  %s

                Please arrive 10 minutes early and bring any previous reports.
                If you can't make it, cancel from "Appointments" in your account so someone else can have the slot.

                %s
                """.formatted(r.patientName(), clinicName, r.doctorName(), r.specialization(),
                WHEN.format(r.scheduledAt().atZone(zone)), r.patientCode(), clinicName);
    }
}
