package com.medicore.policy;

import com.medicore.common.SessionUser;

import java.util.Map;

/**
 * The single decision function for the entire system (Design DD-03, AC-06).
 * Pure over (matrix, injected resolvers): unit-testable without Spring or a database.
 */
public final class PolicyEngine {
    private PolicyEngine() {}

    public static Decision decide(String action, SessionUser user, PolicyContext ctx, RelationshipResolver r) {
        if (PolicyMatrix.PUBLIC_ACTIONS.contains(action)) return Decision.yes("public");

        Map<String, Scope> rule = PolicyMatrix.MATRIX.get(action);
        if (rule == null) return Decision.no("no rule for '" + action + "' (deny-by-default)"); // NFR-SEC-03
        if (user == null) return Decision.no("unauthenticated");

        Scope scope = rule.get(user.role());
        if (scope == null) return Decision.no("role '" + user.role() + "' denied for '" + action + "'");

        return switch (scope) {
            case ANY -> Decision.yes("role:any");
            case OWN -> {
                if (ctx.patientId() == null) yield Decision.no("own-scope requires patient context");
                yield r.patientOwns(user.userId(), ctx.patientId())
                    ? Decision.yes("own") : Decision.no("not your record");
            }
            case RELATIONSHIP -> { // FR-AUTH-05
                if (ctx.patientId() == null) yield Decision.no("relationship-scope requires patient context");
                yield r.doctorHasActiveRelationship(user.staffId(), ctx.patientId())
                    ? Decision.yes("active care relationship") : Decision.no("no active care relationship");
            }
            case WARD -> { // AC-03
                if (ctx.patientId() == null) yield Decision.no("ward-scope requires patient context");
                yield r.nurseWardMatches(user.staffId(), ctx.patientId())
                    ? Decision.yes("assigned ward") : Decision.no("patient not in assigned ward");
            }
            case GRANT -> { // FR-FAM-01/02
                if (ctx.patientId() == null || ctx.scopeNeeded() == null)
                    yield Decision.no("grant-scope requires patient + scope context");
                yield r.grantCovers(user.userId(), ctx.patientId(), ctx.scopeNeeded())
                    ? Decision.yes("valid grant") : Decision.no("no valid grant");
            }
        };
    }
}
