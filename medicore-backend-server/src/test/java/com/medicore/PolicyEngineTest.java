package com.medicore;

import com.medicore.common.SessionUser;
import com.medicore.policy.*;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Negative + positive tests against the SRS §4 matrix (SRS §8 acceptance criteria). */
class PolicyEngineTest {
    private static final UUID P = UUID.randomUUID();
    private static final UUID U = UUID.randomUUID();
    private static final UUID S = UUID.randomUUID();

    private static RelationshipResolver stub(boolean owns, boolean rel, boolean ward, boolean grant) {
        return new RelationshipResolver() {
            public boolean patientOwns(UUID u, UUID p) { return owns; }
            public boolean doctorHasActiveRelationship(UUID s, UUID p) { return rel; }
            public boolean nurseWardMatches(UUID s, UUID p) { return ward; }
            public boolean grantCovers(UUID u, UUID p, String sc) { return grant; }
        };
    }

    @Test void denyByDefaultForUnknownAction() { // NFR-SEC-03
        assertFalse(PolicyEngine.decide("not.a.rule", new SessionUser(U, "sys_admin", null, null),
            PolicyContext.none(), stub(true, true, true, true)).allow());
    }

    @Test void managementNeverReadsEmr() { // AC-02: denied matrix cell
        assertFalse(PolicyEngine.decide("emr.read", new SessionUser(U, "management", null, null),
            PolicyContext.patient(P), stub(true, true, true, true)).allow());
    }

    @Test void doctorWithRelationshipReadsEmr() { // FR-AUTH-05
        assertTrue(PolicyEngine.decide("emr.read", new SessionUser(U, "doctor", S, null),
            PolicyContext.patient(P), stub(false, true, false, false)).allow());
    }

    @Test void doctorWithoutRelationshipDenied() { // ReBAC: role alone is not enough
        assertFalse(PolicyEngine.decide("emr.read", new SessionUser(U, "doctor", S, null),
            PolicyContext.patient(P), stub(false, false, false, false)).allow());
    }

    @Test void patientOwnRecordOnly() {
        var pat = new SessionUser(U, "patient", null, P);
        assertTrue(PolicyEngine.decide("emr.read", pat, PolicyContext.patient(P), stub(true, false, false, false)).allow());
        assertFalse(PolicyEngine.decide("emr.read", pat, PolicyContext.patient(P), stub(false, false, false, false)).allow());
    }

    @Test void nurseScopedToAssignedWard() { // AC-03
        var nurse = new SessionUser(U, "nurse", S, null);
        assertTrue(PolicyEngine.decide("vitals.write", nurse, PolicyContext.patient(P), stub(false, false, true, false)).allow());
        assertFalse(PolicyEngine.decide("vitals.write", nurse, PolicyContext.patient(P), stub(false, false, false, false)).allow());
    }

    @Test void pharmacistReadsRxButNeverNotes() {
        var pharm = new SessionUser(U, "pharmacist", S, null);
        assertTrue(PolicyEngine.decide("rx.read", pharm, PolicyContext.patient(P), stub(false, false, false, false)).allow());
        assertFalse(PolicyEngine.decide("emr.read", pharm, PolicyContext.patient(P), stub(true, true, true, true)).allow());
    }

    @Test void familyGranteeRequiresValidCoveringGrant() { // FR-FAM-01/02
        var fam = new SessionUser(U, "family", null, null);
        assertTrue(PolicyEngine.decide("invoice.read", fam, PolicyContext.grant(P, "billing"), stub(false, false, false, true)).allow());
        assertFalse(PolicyEngine.decide("invoice.read", fam, PolicyContext.grant(P, "billing"), stub(false, false, false, false)).allow());
    }

    @Test void onlyManagementVoidsInvoices() { // FR-BIL-07
        assertTrue(PolicyEngine.decide("invoice.void", new SessionUser(U, "management", null, null),
            PolicyContext.none(), stub(false, false, false, false)).allow());
        assertFalse(PolicyEngine.decide("invoice.void", new SessionUser(U, "billing_clerk", S, null),
            PolicyContext.none(), stub(true, true, true, true)).allow());
    }

    @Test void unauthenticatedDeniedOnProtectedActions() {
        assertFalse(PolicyEngine.decide("slot.list", null, PolicyContext.none(), stub(true, true, true, true)).allow());
    }

    @Test void publicRegistrationAllowedUnauthenticated() {
        assertTrue(PolicyEngine.decide("patient.register_self", null, PolicyContext.none(),
            stub(false, false, false, false)).allow());
    }
}
