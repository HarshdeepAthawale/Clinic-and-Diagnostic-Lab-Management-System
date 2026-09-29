package com.cdlms.result;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * Picks how "your report is ready" reaches the patient: SMTP when {@code spring.mail.host} is set
 * (Mailpit in local development), otherwise a log line. The email carries no clinical content — it
 * points the patient to their account, where the report sits behind the normal sign-in.
 */
@Configuration
public class ReportMailConfig {

    private static final Logger log = LoggerFactory.getLogger(ReportMailConfig.class);

    @Bean
    ReportMailer reportMailer(ObjectProvider<JavaMailSender> mail,
                              @Value("${app.reminders.from:CDLMS Clinic <no-reply@cdlms.local>}") String from,
                              @Value("${app.clinic.name:CDLMS Clinic}") String clinicName,
                              @Value("${app.public-url:http://localhost:3000}") String publicUrl) {
        JavaMailSender sender = mail.getIfAvailable();
        if (sender == null) {
            log.warn("spring.mail.host is not set: report notices will be logged, not emailed");
            return notice -> log.info("Mail not configured; report notice for sample {} not emailed", notice.sampleCode());
        }
        return notice -> {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(from);
            message.setTo(notice.email());
            message.setSubject("Your lab report is ready");
            message.setText(body(notice, clinicName, publicUrl));
            sender.send(message);
        };
    }

    static String body(ReportMailer.Notice n, String clinicName, String publicUrl) {
        return """
                Hello %s,

                Your lab report from %s is ready:

                  Sample:  %s
                  Tests:   %s

                For your privacy the report isn't attached. Sign in at %s and open "Reports" to read or download it.

                %s
                """.formatted(n.patientName(), clinicName, n.sampleCode(), String.join(", ", n.testNames()),
                publicUrl, clinicName);
    }
}
