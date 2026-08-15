package com.medicore.audit;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;

/** Append-only audit writes (FR-EMR-06, NFR-SEC-04). Failures never break the request path. */
@Service
public class AuditService {
    private final JdbcTemplate jdbc;
    public AuditService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public void log(UUID userId, UUID patientId, String action, String entityRef, String metaJson) {
        try {
            jdbc.update(
                "INSERT INTO audit_log (user_id, patient_id, action, entity_ref, meta) VALUES (?,?,?,?,?::jsonb)",
                userId, patientId, action, entityRef, metaJson);
        } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger(AuditService.class).error("audit write failed: {}", e.getMessage());
        }
    }
}
