package com.medicore.payments;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * DD-07: the payment port. Billing logic, database states and tests are written against
 * this contract; the ITC adapter is the only class that changes when the API
 * specification arrives (SRS Open Issue OI-5, Milestone 3).
 */
public interface PaymentGateway {
    record PaymentRequest(UUID invoiceId, String localReference, BigDecimal amount, String customerEmail) {}
    record PaymentInstruction(String gatewayReference, String redirectUrl) {}
    record VerificationResult(boolean success, BigDecimal amount, String rawStatus) {}

    PaymentInstruction requestPayment(PaymentRequest request);
    /** Independent server-side status query — the callback alone never credits (NFR-SEC-06). */
    VerificationResult verifyStatus(String gatewayReference);
}
