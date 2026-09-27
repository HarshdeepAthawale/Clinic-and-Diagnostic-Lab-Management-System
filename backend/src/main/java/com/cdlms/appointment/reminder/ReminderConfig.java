package com.cdlms.appointment.reminder;

import com.cdlms.common.ClinicTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Picks the reminder channel (SMTP when {@code spring.mail.host} is set, otherwise a log line) and
 * schedules {@link ReminderJob} unless {@code app.reminders.enabled=false}.
 */
@Configuration
public class ReminderConfig {

    private static final Logger log = LoggerFactory.getLogger(ReminderConfig.class);

    @Bean
    ReminderSender reminderSender(ObjectProvider<JavaMailSender> mail, ReminderProperties properties, ClinicTime time) {
        JavaMailSender sender = mail.getIfAvailable();
        if (sender == null) {
            log.warn("spring.mail.host is not set: appointment reminders will be logged, not emailed");
            return new LoggingReminderSender();
        }
        return new MailReminderSender(sender, properties, time.zone());
    }

    @Configuration
    @EnableScheduling
    @ConditionalOnBooleanProperty(name = "app.reminders.enabled", matchIfMissing = true)
    static class Schedule {

        private final ReminderJob job;

        Schedule(ReminderJob job) {
            this.job = job;
        }

        @Scheduled(initialDelayString = "PT30S", fixedDelayString = "${app.reminders.poll-interval:PT5M}")
        void run() {
            int sent = job.sendDue();
            if (sent > 0) {
                log.info("Sent {} appointment reminder(s)", sent);
            }
        }
    }
}
