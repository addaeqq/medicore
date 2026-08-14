package com.medicore.payments;

import com.medicore.payments.PaymentGateway.VerificationResult;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Pure mapping of ITC Transflow Checkout response shapes (API Definition, IT Consortium).
 * Framework-free so it is unit-verified against the spec's own sample payloads.
 *
 * Shapes handled:
 *  - /request-payments 200: { responseCode: 200, data: { transactionReference, checkoutUrl|paymentLink } }
 *  - /check-transaction-status 200: { responseCode: 200, data: { amount, refNo,
 *        responseCode: "01" (success) | other (failure), responseMessage } }
 *  - callback body: { refNo | transactionReference, amount: '12.00', responseCode: '01'|'100', ... }
 */
public final class ItcResponseMapper {
    private ItcResponseMapper() {}

    public record InitResult(String transactionReference, String checkoutUrl) {}

    @SuppressWarnings("unchecked")
    private static Map<String, Object> data(Map<String, Object> body) {
        Object d = body == null ? null : body.get("data");
        return d instanceof Map ? (Map<String, Object>) d : null;
    }

    private static boolean outerOk(Map<String, Object> body) {
        Object code = body == null ? null : body.get("responseCode");
        return code != null && "200".equals(String.valueOf(code));
    }

    /** Amounts appear as JSON numbers (17) or strings ('12.00'); both must parse exactly. */
    public static BigDecimal toAmount(Object raw) {
        if (raw == null) return null;
        try {
            return new BigDecimal(String.valueOf(raw).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static InitResult mapInitResponse(Map<String, Object> body) {
        Map<String, Object> data = data(body);
        if (!outerOk(body) || data == null)
            throw new IllegalStateException("ITC init rejected: " +
                (body == null ? "empty response" : String.valueOf(body.get("responseMessage"))));
        Object ref = data.get("transactionReference");
        if (ref == null) throw new IllegalStateException("ITC init response missing transactionReference");
        Object url = data.get("checkoutUrl") != null ? data.get("checkoutUrl") : data.get("paymentLink");
        if (url == null) throw new IllegalStateException("ITC init response missing checkoutUrl/paymentLink");
        return new InitResult(String.valueOf(ref), String.valueOf(url));
    }

    /** Success requires outer 200 AND inner data.responseCode == "01" (spec §3). Fail closed otherwise. */
    public static VerificationResult mapStatusResponse(Map<String, Object> body) {
        Map<String, Object> data = data(body);
        if (!outerOk(body) || data == null)
            return new VerificationResult(false, null,
                body == null ? "empty response" : String.valueOf(body.get("responseMessage")));
        boolean success = "01".equals(String.valueOf(data.get("responseCode")));
        return new VerificationResult(success, toAmount(data.get("amount")),
            String.valueOf(data.get("responseMessage")));
    }

    /** Callback carries our reference as refNo (== transactionReference from init). */
    public static String extractReference(Map<String, Object> callbackBody) {
        if (callbackBody == null) return null;
        for (String key : new String[]{"refNo", "transactionReference", "reference"}) {
            Object v = callbackBody.get(key);
            if (v != null && !String.valueOf(v).isBlank()) return String.valueOf(v);
        }
        return null;
    }
}
