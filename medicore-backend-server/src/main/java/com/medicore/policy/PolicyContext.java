package com.medicore.policy;

import java.util.UUID;

/** Resource context an authorisation decision may need. */
public record PolicyContext(UUID patientId, String scopeNeeded) {
    public static PolicyContext none() { return new PolicyContext(null, null); }
    public static PolicyContext patient(UUID patientId) { return new PolicyContext(patientId, null); }
    public static PolicyContext grant(UUID patientId, String scopeNeeded) { return new PolicyContext(patientId, scopeNeeded); }
}
