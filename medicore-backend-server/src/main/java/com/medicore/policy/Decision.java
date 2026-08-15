package com.medicore.policy;

/** Outcome of a policy evaluation; reason is logged, never leaked to clients. */
public record Decision(boolean allow, String reason) {
    static Decision yes(String reason) { return new Decision(true, reason); }
    static Decision no(String reason) { return new Decision(false, reason); }
}
