package com.medicore.payments;

import com.medicore.common.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * ITC Transflow Checkout adapter (DD-07 implementation; SRS OI-5 resolved Aug 2026).
 *
 * Flow per the API Definition:
 *  1. POST {base}/request-payments  -> data.transactionReference + data.checkoutUrl (redirect target)
 *  2. ITC POSTs a callback to our /api/payments/callback carrying refNo (== transactionReference);
 *     we must always answer HTTP 200 (handled in BillingController)
 *  3. POST {base}/check-transaction-status is the independent verification (NFR-SEC-06):
 *     success only when outer responseCode == 200 AND data.responseCode == "01",
 *     and BillingService additionally requires the exact amount match.
 *
 * Authentication is body-level per the spec: apiKey + merchantProductId + transflowId.
 * UAT base URL and test cards are in the README; response-shape mapping lives in
 * ItcResponseMapper (pure, verified against the spec's sample payloads).
 */
@Component
public class ItcGatewayAdapter implements PaymentGateway {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ItcGatewayAdapter.class);

    private static final ParameterizedTypeReference<Map<String, Object>> MAP =
        new ParameterizedTypeReference<>() {};

    private final RestClient http;
    private final String apiKey;
    private final String merchantProductId;
    private final String transflowId;
    private final String currency;
    private final String callbackUrl;
    private final String successRedirectUrl;
    private final String failureRedirectUrl;
    private final String pageTitle;
    private final String logo;

    public ItcGatewayAdapter(
            @Value("${medicore.itc.base-url}") String baseUrl,
            @Value("${medicore.itc.api-key}") String apiKey,
            @Value("${medicore.itc.merchant-product-id}") String merchantProductId,
            @Value("${medicore.itc.transflow-id}") String transflowId,
            @Value("${medicore.itc.currency}") String currency,
            @Value("${medicore.itc.callback-url}") String callbackUrl,
            @Value("${medicore.itc.success-redirect-url}") String successRedirectUrl,
            @Value("${medicore.itc.failure-redirect-url}") String failureRedirectUrl,
            @Value("${medicore.itc.page-title}") String pageTitle,
            @Value("${medicore.itc.logo}") String logo) {
        this.http = RestClient.builder().baseUrl(baseUrl).build();
        this.apiKey = apiKey;
        this.merchantProductId = merchantProductId;
        this.transflowId = transflowId;
        this.currency = currency;
        this.callbackUrl = callbackUrl;
        this.successRedirectUrl = successRedirectUrl;
        this.failureRedirectUrl = failureRedirectUrl;
        this.pageTitle = pageTitle;
        this.logo = logo;
    }

    private void requireConfigured() {
        if (isBlank(apiKey) || isBlank(merchantProductId) || isBlank(transflowId))
            throw new ApiException(503,
                "ITC Payments is not configured: set ITC_API_KEY, ITC_MERCHANT_PRODUCT_ID and ITC_TRANSFLOW_ID");
    }

    private static boolean isBlank(String s) { return s == null || s.isBlank(); }

    @Override
    public PaymentInstruction requestPayment(PaymentRequest request) {
        requireConfigured();
        if (isBlank(request.customerEmail()))
            throw new ApiException(422, "An email address is required for online payment");

        // Field set per API Definition §1 (mandatory: fullName, email, narration, amount,
        // currency, successRedirectUrl, failureRedirectUrl, pageTitle, apiKey,
        // merchantProductId, transflowId; callbackUrl per the sample request).
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("fullName", isBlank(request.customerName()) ? "MediCore Patient" : request.customerName());
        body.put("email", request.customerEmail());
        body.put("narration", "MediCore invoice payment " + request.localReference());
        body.put("amount", request.amount());
        body.put("currency", currency);
        body.put("successRedirectUrl", successRedirectUrl);
        body.put("failureRedirectUrl", failureRedirectUrl);
        body.put("callbackUrl", callbackUrl);
        body.put("pageTitle", pageTitle);
        body.put("pageDescription", "MediCore HMS - hospital bill payment");
        if (!isBlank(logo)) body.put("logo", logo);
        body.put("apiKey", apiKey);
        body.put("merchantProductId", merchantProductId);
        body.put("transflowId", transflowId);

        Map<String, Object> response;
        try {
            response = http.post().uri("/request-payments")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(MAP);
        } catch (RestClientException e) {
            // The client message stays generic (NFR-SEC-02), but the operator needs the vendor's
            // own reason - an unauthorised merchant and a network outage are not the same incident.
            logGatewayFailure("request-payments", e);
            throw new ApiException(502, "Payment gateway unavailable - please try again");
        }
        try {
            var init = ItcResponseMapper.mapInitResponse(response);
            return new PaymentInstruction(init.transactionReference(), init.checkoutUrl());
        } catch (IllegalStateException e) {
            throw new ApiException(502, "Payment gateway rejected the request");
        }
    }

    @Override
    public VerificationResult verifyStatus(String gatewayReference) {
        requireConfigured();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("transactionReference", gatewayReference);
        body.put("apiKey", apiKey);
        body.put("merchantProductId", merchantProductId);
        body.put("transflowId", transflowId);
        try {
            Map<String, Object> response = http.post().uri("/check-transaction-status")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(MAP);
            return ItcResponseMapper.mapStatusResponse(response); // fail-closed mapping
        } catch (RestClientException e) {
            // Transport failure must never credit; surface as retryable (NFR-SEC-06 fail closed).
            logGatewayFailure("check-transaction-status", e);
            throw new ApiException(502, "Verification unavailable - payment remains pending; retry verification");
        }
    }

    /**
     * Server-side diagnostics for a failed gateway call. Credentials are never logged; the
     * vendor's status line and body are, because they carry the actual fault (for example
     * "Vendor not authorized to access this service" when the merchant is not enabled for
     * Transflow Checkout, which is indistinguishable from an outage at the HTTP layer).
     */
    private void logGatewayFailure(String endpoint, RestClientException e) {
        if (e instanceof RestClientResponseException http) {
            log.error("ITC {} rejected the call: HTTP {} {} body={}", endpoint,
                http.getStatusCode().value(), http.getStatusText(), http.getResponseBodyAsString());
        } else {
            log.error("ITC {} could not be reached: {}", endpoint, e.getMessage());
        }
    }

    @Override
    public String extractReference(Map<String, Object> callbackBody) {
        return ItcResponseMapper.extractReference(callbackBody);
    }
}
