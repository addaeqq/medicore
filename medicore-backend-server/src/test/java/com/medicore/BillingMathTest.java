package com.medicore;

import com.medicore.billing.BillingMath;
import com.medicore.billing.PaymentVerifier;
import com.medicore.payments.PaymentGateway.VerificationResult;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Pure tests for invoice arithmetic (DD-05) and the verify-before-credit rule (NFR-SEC-06). */
class BillingMathTest {
    private static BigDecimal d(String s) { return new BigDecimal(s); }

    @Test
    void sumsExactlyAndIgnoresNulls() {
        assertEquals(0, BillingMath.sum(List.of(d("80.00"), d("12.50"))).compareTo(d("92.50")));
        assertEquals(0, BillingMath.sum(Arrays.asList(d("1.00"), null)).compareTo(d("1.00")));
    }

    @Test
    void statusDerivation() {
        assertEquals("issued", BillingMath.deriveStatus(d("100"), d("0")));
        assertEquals("partially_paid", BillingMath.deriveStatus(d("100"), d("40")));
        assertEquals("paid", BillingMath.deriveStatus(d("100"), d("100.00")));
        assertEquals("paid", BillingMath.deriveStatus(d("100"), d("120")));
        assertEquals("paid", BillingMath.deriveStatus(d("0"), d("0")));
    }

    @Test
    void balanceFloorsAtZero() {
        assertEquals(0, BillingMath.balance(d("92.50"), d("40")).compareTo(d("52.50")));
        assertEquals(0, BillingMath.balance(d("100"), d("120")).signum());
    }

    @Test
    void verifierCreditsOnlyExactSuccessfulVerification() {
        var expected = d("92.50");
        assertTrue(PaymentVerifier.evaluate(new VerificationResult(true, d("92.50"), "success"), expected).credit());
        assertFalse(PaymentVerifier.evaluate(new VerificationResult(false, d("92.50"), "failed"), expected).credit());
        assertFalse(PaymentVerifier.evaluate(new VerificationResult(true, d("90.00"), "success"), expected).credit());
        assertFalse(PaymentVerifier.evaluate(new VerificationResult(true, null, "success"), expected).credit());
        assertFalse(PaymentVerifier.evaluate(null, expected).credit());
        assertFalse(PaymentVerifier.evaluate(new VerificationResult(true, d("1"), "success"), d("0")).credit());
    }
}
