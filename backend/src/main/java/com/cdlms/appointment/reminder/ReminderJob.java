package com.cdlms.appointment.reminder;

import com.cdlms.common.ClinicTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Sends one reminder per booked appointment once it is within {@code app.reminders.lead-time}
 * (Phase 03 exit criterion). Rows are claimed with {@code FOR UPDATE SKIP LOCKED}, so several
 * backend instances never send the same reminder twice. Patients without a login have no email
 * address and are skipped (SMS is ADR-007, still open).
 */
@Component
public class ReminderJob {

    private static final Logger log = LoggerFactory.getLogger(ReminderJob.class);
    private static final int BATCH = 50;

    private final NamedParameterJdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final ReminderSender sender;
    private final ReminderProperties properties;
    private final ClinicTime time;

    public ReminderJob(NamedParameterJdbcTemplate jdbc, TransactionTemplate tx, ReminderSender sender,
                       ReminderProperties properties, ClinicTime time) {
        this.jdbc = jdbc;
        this.tx = tx;
        this.sender = sender;
        this.properties = properties;
        this.time = time;
    }

    /** Sends every reminder that is due now; returns how many were sent. */
    public int sendDue() {
        int total = 0;
        int sent;
        do {
            sent = tx.execute(status -> sendBatch());
            total += sent;
        } while (sent == BATCH);
        return total;
    }

    private int sendBatch() {
        Instant now = time.now();
        List<Reminder> due = jdbc.query("""
                SELECT a.id, u.email, p.full_name AS patient_name, p.patient_code,
                       d.full_name AS doctor_name, d.specialization, a.scheduled_at
                FROM appointments a
                JOIN patients p ON p.id = a.patient_id
                JOIN users u ON u.id = p.user_id AND u.is_active
                JOIN doctors d ON d.id = a.doctor_id
                WHERE a.status = 'BOOKED' AND a.reminder_sent_at IS NULL
                  AND a.scheduled_at > :now AND a.scheduled_at <= :until
                ORDER BY a.scheduled_at
                LIMIT :batch
                FOR UPDATE OF a SKIP LOCKED
                """, new MapSqlParameterSource("now", Timestamp.from(now))
                .addValue("until", Timestamp.from(now.plus(properties.leadTime())))
                .addValue("batch", BATCH), (rs, i) -> new Reminder(rs.getObject("id", UUID.class),
                rs.getString("email"), rs.getString("patient_name"), rs.getString("patient_code"),
                rs.getString("doctor_name"), rs.getString("specialization"),
                rs.getTimestamp("scheduled_at").toInstant()));

        int sent = 0;
        for (Reminder reminder : due) {
            try {
                sender.send(reminder);
            } catch (RuntimeException e) {
                // Left unmarked, so the next run tries again.
                log.warn("Reminder for appointment {} not sent: {}", reminder.appointmentId(), e.getMessage());
                continue;
            }
            jdbc.update("UPDATE appointments SET reminder_sent_at = :now WHERE id = :id",
                    new MapSqlParameterSource("now", Timestamp.from(now)).addValue("id", reminder.appointmentId()));
            sent++;
        }
        // Any failure makes this < BATCH, so sendDue() stops instead of retrying the same rows in a loop.
        return sent;
    }
}
