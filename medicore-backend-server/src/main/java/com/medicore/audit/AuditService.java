package com.medicore.audit;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Append-only audit writes (FR-EMR-06, NFR-SEC-04). Failures never break the request path.
 *
 * REQUIRES_NEW is what makes that promise true. The write used to join the caller's
 * transaction, so a rejected audit row — a bad FK, say — aborted the whole PostgreSQL
 * transaction; the catch below then swallowed the error and the caller was told its
 * business write had succeeded while the commit silently discarded it. Isolating the
 * insert means an audit failure can only ever cost the audit row.
 *
 * It also makes the record honest in the other direction: an attempt is written even
 * when the surrounding business transaction rolls back, which is what a security trail
 * is for — denials and failures are exactly what you want recorded (FR-EMR-06).
 */
@Service
public class AuditService {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(AuditService.class);

    private final JdbcTemplate jdbc;
    public AuditService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(UUID userId, UUID patientId, String action, String entityRef, String metaJson) {
        try {
            jdbc.update(
                "INSERT INTO audit_log (user_id, patient_id, action, entity_ref, meta) VALUES (?,?,?,?,?::jsonb)",
                userId, patientId, action, entityRef, metaJson);
        } catch (Exception e) {
            // Losing an audit row is bad; losing the clinical or financial write it describes
            // would be worse, so this stays non-fatal — but it is logged loudly enough to notice.
            log.error("audit write failed for action '{}' (user={}, ref={}): {}",
                action, userId, entityRef, e.getMessage());
        }
    }
}
