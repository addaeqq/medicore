package com.medicore.notifications;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Drains the notification outbox (TD-02's durable replacement). Claims due rows
 * with FOR UPDATE SKIP LOCKED so multiple instances never double-send; failures
 * back off exponentially (OutboxPolicy) and become terminally 'failed' after
 * MAX_ATTEMPTS for the weekly ops review. Unconfigured mail -> rows 'skipped',
 * so business flows never depend on an email provider existing.
 */
@Component
public class OutboxWorker {
    private static final Logger log = LoggerFactory.getLogger(OutboxWorker.class);
    private final JdbcTemplate jdbc;
    private final MailPort mail;
    private final NotificationService notifications;

    public OutboxWorker(JdbcTemplate jdbc, MailPort mail, NotificationService notifications) {
        this.jdbc = jdbc; this.mail = mail; this.notifications = notifications;
    }

    @Scheduled(fixedDelayString = "${medicore.notifications.drain-ms:30000}")
    @Transactional
    public void drain() { drainOnce(20); }

    /** Separated for testability; returns rows processed. */
    @Transactional
    public int drainOnce(int batch) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT notification_id, recipient, subject, body_text, attempts
            FROM notification_outbox
            WHERE status = 'pending' AND next_attempt_at <= now()
            ORDER BY created_at
            LIMIT ? FOR UPDATE SKIP LOCKED
            """, batch);
        for (Map<String, Object> r : rows) {
            UUID id = (UUID) r.get("notification_id");
            int attempts = (int) r.get("attempts");
            if (!mail.configured()) {
                jdbc.update("UPDATE notification_outbox SET status='skipped', " +
                    "last_error='mail not configured' WHERE notification_id=?", id);
                continue;
            }
            try {
                mail.send((String) r.get("recipient"), (String) r.get("subject"), (String) r.get("body_text"));
                jdbc.update("UPDATE notification_outbox SET status='sent', sent_at=now() WHERE notification_id=?", id);
            } catch (Exception e) {
                int next = attempts + 1;
                if (OutboxPolicy.exhausted(next)) {
                    jdbc.update("UPDATE notification_outbox SET status='failed', attempts=?, last_error=? " +
                        "WHERE notification_id=?", next, trim(e.getMessage()), id);
                    log.warn("notification {} failed terminally: {}", id, e.getMessage());
                } else {
                    jdbc.update("UPDATE notification_outbox SET attempts=?, last_error=?, " +
                        "next_attempt_at = now() + make_interval(mins => ?) WHERE notification_id=?",
                        next, trim(e.getMessage()), (int) OutboxPolicy.nextDelayMinutes(next), id);
                }
            }
        }
        return rows.size();
    }

    @Scheduled(fixedDelayString = "${medicore.notifications.reminder-scan-ms:3600000}")
    public void reminders() {
        int n = notifications.enqueueDueReminders();
        if (n > 0) log.info("reminder scan: {} appointment(s) in the next 24h", n);
    }

    private static String trim(String s) { return s == null ? null : s.substring(0, Math.min(s.length(), 500)); }
}
