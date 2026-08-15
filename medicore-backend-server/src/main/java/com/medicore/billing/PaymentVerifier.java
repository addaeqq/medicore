package com.medicore.billing;

import com.medicore.payments.PaymentGateway.VerificationResult;

import java.math.BigDecimal;

/**
 * NFR-SEC-06 as a pure, testable rule: a payment is credited ONLY when an independent
 * server-side status query succeeds AND the verified amount exactly matches what we
 * expect. The callback body itself never credits anything. Framework-free.
 */
public final class PaymentVerifier {
    private PaymentVerifier() {}

    public record Outcome(boolean credit, String reason) {}

    public static Outcome evaluate(VerificationResult vr, BigDecimal expectedAmount) {
        if (vr == null) return new Outcome(false, "no verification result");
        if (!vr.success()) return new Outcome(false, "gateway reports non-success: " + vr.rawStatus());
        if (vr.amount() == null) return new Outcome(false, "gateway returned no amount");
        if (expectedAmount == null || expectedAmount.signum() <= 0)
            return new Outcome(false, "invalid expected amount");
        if (vr.amount().compareTo(expectedAmount) != 0)
            return new Outcome(false, "amount mismatch: expected " + expectedAmount + ", verified " + vr.amount());
        return new Outcome(true, "verified");
    }
}
