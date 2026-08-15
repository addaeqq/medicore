package com.medicore.notifications;

import com.medicore.notifications.EmailTemplates.Email;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Outbox producer (TD-08 / FR-APT-06, retiring TD-02): notifications are enqueued
 * in the SAME transaction as the business change, so a booking and its confirmation
 * row commit or roll back together; delivery is the worker's problem. Enqueue is
 * idempotent via UNIQUE(template, ref_id). Patients without an email (walk-ins)
 * are silently skipped - email is best-effort by requirement.
 */
@Service
public class NotificationService {
    private final JdbcTemplate jdbc;

    public NotificationService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    private void enqueue(String template, UUID refId, String recipient, Email email) {
        if (recipient == null || recipient.isBlank()) return;
        jdbc.update("""
            INSERT INTO notification_outbox (template, ref_id, recipient, subject, body_text)
            VALUES (?, ?, ?, ?, ?)
            ON CONFLICT (template, ref_id) DO NOTHING
            """, template, refId, recipient, email.subject(), email.body());
    }

    private Map<String, Object> appointmentContext(UUID appointmentId) {
        return jdbc.queryForList("""
            SELECT p.full_name AS patient, u.email, st.full_name AS doctor,
                   d.name AS department, sl.starts_at
            FROM appointments a
            JOIN patients p ON p.patient_id = a.patient_id
            LEFT JOIN users u ON u.user_id = p.user_id
            JOIN slots sl ON sl.slot_id = a.slot_id
            JOIN staff st ON st.staff_id = sl.doctor_id
            JOIN departments d ON d.department_id = a.department_id
            WHERE a.appointment_id = ?
            """, appointmentId).stream().findFirst().orElse(null);
    }

    private static Instant instantOf(Object ts) { return ((java.sql.Timestamp) ts).toInstant(); }

    public void appointmentBooked(UUID appointmentId) {
        Map<String, Object> c = appointmentContext(appointmentId);
        if (c == null) return;
        enqueue("booking_confirmation", appointmentId, (String) c.get("email"),
            EmailTemplates.bookingConfirmation((String) c.get("patient"), (String) c.get("doctor"),
                (String) c.get("department"), instantOf(c.get("starts_at"))));
    }

    public void appointmentCancelled(UUID appointmentId) {
        Map<String, Object> c = appointmentContext(appointmentId);
        if (c == null) return;
        enqueue("cancellation", appointmentId, (String) c.get("email"),
            EmailTemplates.cancellation((String) c.get("patient"), (String) c.get("doctor"),
                instantOf(c.get("starts_at"))));
    }

    public void paymentReceived(UUID paymentId, BigDecimal amount) {
        jdbc.queryForList("""
            SELECT pt.full_name AS patient, u.email, COALESCE(i.visit_ref, i.invoice_id::text) AS ref
            FROM payments pm
            JOIN invoices i ON i.invoice_id = pm.invoice_id
            JOIN patients pt ON pt.patient_id = i.patient_id
            LEFT JOIN users u ON u.user_id = pt.user_id
            WHERE pm.payment_id = ?
            """, paymentId).stream().findFirst().ifPresent(c ->
            enqueue("payment_receipt", paymentId, (String) c.get("email"),
                EmailTemplates.paymentReceipt((String) c.get("patient"), amount, (String) c.get("ref"))));
    }

    /** Reminders for appointments starting in the next 24h; UNIQUE(template, ref_id) makes re-runs harmless. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int enqueueDueReminders() {
        var due = jdbc.queryForList("""
            SELECT a.appointment_id FROM appointments a
            JOIN slots sl ON sl.slot_id = a.slot_id
            WHERE a.status IN ('booked','checked_in')
              AND sl.starts_at BETWEEN now() AND now() + interval '24 hours'
            """);
        for (Map<String, Object> row : due) {
            UUID id = (UUID) row.get("appointment_id");
            Map<String, Object> c = appointmentContext(id);
            if (c == null) continue;
            enqueue("reminder", id, (String) c.get("email"),
                EmailTemplates.reminder((String) c.get("patient"), (String) c.get("doctor"),
                    (String) c.get("department"), instantOf(c.get("starts_at"))));
        }
        return due.size();
    }
}
