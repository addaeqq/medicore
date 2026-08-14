package com.medicore.payments;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

/**
 * DD-07: the payment port. Billing logic, database states and tests are written against
 * this contract; only the adapter changes per vendor. Implemented for ITC Transflow
 * Checkout (spec received Aug 2026 — SRS OI-5 resolved).
 */
public interface PaymentGateway {
    record PaymentRequest(UUID invoiceId, String localReference, BigDecimal amount,
                          String customerName, String customerEmail) {}
    record PaymentInstruction(String gatewayReference, String redirectUrl) {}
    record VerificationResult(boolean success, BigDecimal amount, String rawStatus) {}

    PaymentInstruction requestPayment(PaymentRequest request);

    /** Independent server-side status query — the callback alone never credits (NFR-SEC-06). */
    VerificationResult verifyStatus(String gatewayReference);

    /** Extract our stored reference from a vendor callback body (vendor shape stays in the adapter). */
    String extractReference(Map<String, Object> callbackBody);
}
