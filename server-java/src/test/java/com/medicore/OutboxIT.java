package com.medicore;

import com.medicore.notifications.MailPort;
import com.medicore.notifications.NotificationService;
import com.medicore.notifications.OutboxPolicy;
import com.medicore.notifications.OutboxWorker;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Outbox state machine (DD-08, retires TD-02): pending -> sent on success;
 * pending -> backoff -> failed after MAX_ATTEMPTS; skipped when mail is
 * unconfigured; enqueue is idempotent per (template, ref_id).
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "MEDICORE_IT", matches = "true")
class OutboxIT {

    @TestConfiguration
    static class StubMail {
        static final AtomicBoolean configured = new AtomicBoolean(true);
        static final AtomicBoolean failNext = new AtomicBoolean(false);
        static final AtomicInteger sent = new AtomicInteger();
        @Bean @Primary
        MailPort stub() {
            return new MailPort() {
                public boolean configured() { return configured.get(); }
                public void send(String r, String s, String b) {
                    if (failNext.get()) throw new RuntimeException("smtp down (stub)");
                    sent.incrementAndGet();
                }
            };
        }
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired OutboxWorker worker;
    @Autowired NotificationService notifications;

    private UUID enqueueRaw(String template, UUID ref) {
        jdbc.update("""
            INSERT INTO notification_outbox (template, ref_id, recipient, subject, body_text)
            VALUES (?,?,?,?,?) ON CONFLICT (template, ref_id) DO NOTHING
            """, template, ref, "t@t.test", "s", "b");
        return ref;
    }

    private String status(UUID ref) {
        return jdbc.queryForObject(
            "SELECT status FROM notification_outbox WHERE ref_id = ?", String.class, ref);
    }

    @Test
    void successfulSendMarksSent() {
        StubMail.configured.set(true); StubMail.failNext.set(false);
        UUID ref = enqueueRaw("booking_confirmation", UUID.randomUUID());
        worker.drainOnce(50);
        assertEquals("sent", status(ref));
    }

    @Test
    void transportFailureBacksOffThenFailsTerminally() {
        StubMail.configured.set(true); StubMail.failNext.set(true);
        UUID ref = enqueueRaw("cancellation", UUID.randomUUID());
        worker.drainOnce(50);
        assertEquals("pending", status(ref)); // backed off, not dead
        int attempts = jdbc.queryForObject(
            "SELECT attempts FROM notification_outbox WHERE ref_id=?", Integer.class, ref);
        assertEquals(1, attempts);
        // fast-forward the clock past every backoff and exhaust the attempts
        for (int i = 0; i < OutboxPolicy.MAX_ATTEMPTS; i++) {
            jdbc.update("UPDATE notification_outbox SET next_attempt_at = now() WHERE ref_id=?", ref);
            worker.drainOnce(50);
        }
        assertEquals("failed", status(ref));
        StubMail.failNext.set(false);
    }

    @Test
    void unconfiguredMailSkipsWithoutBreakingAnything() {
        StubMail.configured.set(false);
        UUID ref = enqueueRaw("reminder", UUID.randomUUID());
        worker.drainOnce(50);
        assertEquals("skipped", status(ref));
        StubMail.configured.set(true);
    }

    @Test
    void enqueueIsIdempotentPerTemplateAndRef() {
        UUID ref = UUID.randomUUID();
        enqueueRaw("payment_receipt", ref);
        enqueueRaw("payment_receipt", ref);
        Integer n = jdbc.queryForObject(
            "SELECT count(*) FROM notification_outbox WHERE ref_id=?", Integer.class, ref);
        assertEquals(1, n);
    }
}
