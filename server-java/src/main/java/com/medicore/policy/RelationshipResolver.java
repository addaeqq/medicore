package com.medicore.policy;

import java.util.UUID;

/** ReBAC resolvers (FR-AUTH-05, AC-03, FR-FAM-01/02). Injected so PolicyEngine is unit-testable without a database. */
public interface RelationshipResolver {
    boolean patientOwns(UUID userId, UUID patientId);
    boolean doctorHasActiveRelationship(UUID doctorStaffId, UUID patientId);
    boolean nurseWardMatches(UUID nurseStaffId, UUID patientId);
    boolean grantCovers(UUID granteeUserId, UUID patientId, String scopeNeeded);
}
