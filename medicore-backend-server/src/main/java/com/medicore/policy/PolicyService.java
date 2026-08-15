package com.medicore.policy;

import com.medicore.audit.AuditService;
import com.medicore.common.ApiException;
import com.medicore.common.SessionUser;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Service;

/**
 * Spring-facing wrapper around the pure PolicyEngine: the ONLY authorisation path (AC-06).
 * Audits clinical actions allowed-or-denied (FR-EMR-06), then throws 401/403 on denial.
 */
@Service
public class PolicyService {
    private final RelationshipResolver resolver;
    private final AuditService audit;

    public PolicyService(RelationshipResolver resolver, AuditService audit) {
        this.resolver = resolver;
        this.audit = audit;
    }

    public SessionUser currentUser(HttpSession session) {
        return session == null ? null : (SessionUser) session.getAttribute("user");
    }

    public SessionUser authorize(HttpSession session, String action, PolicyContext ctx) {
        SessionUser user = currentUser(session);
        Decision d = PolicyEngine.decide(action, user, ctx, resolver);
        if (PolicyMatrix.AUDITED_ACTIONS.contains(action)) {
            String meta = "{\"allow\":" + d.allow() + ",\"reason\":\"" + d.reason().replace("\"", "'") + "\"}";
            audit.log(user == null ? null : user.userId(), ctx.patientId(), action, null, meta);
        }
        if (!d.allow()) throw new ApiException(user == null ? 401 : 403, "Not permitted");
        return user;
    }
}
