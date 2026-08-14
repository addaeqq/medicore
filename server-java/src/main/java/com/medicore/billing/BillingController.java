package com.medicore.billing;

import com.medicore.common.SessionUser;
import com.medicore.policy.PolicyContext;
import com.medicore.policy.PolicyService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class BillingController {
    private final BillingService billing;
    private final PolicyService policy;
    private final com.medicore.payments.PaymentGateway gateway;

    public BillingController(BillingService billing, PolicyService policy,
                             com.medicore.payments.PaymentGateway gateway) {
        this.billing = billing; this.policy = policy; this.gateway = gateway;
    }

    public record CreateInvoiceRequest(@NotNull UUID patientId, String visitRef) {}
    public record AddItemRequest(@NotBlank @Pattern(regexp = "consultation|pharmacy|laboratory|bed_day|other")
                                 String sourceType, UUID sourceId,
                                 @NotBlank String description,
                                 @NotNull @DecimalMin("0.0") BigDecimal amount) {}
    public record ConsultChargeRequest(@NotNull UUID consultationId) {}
    public record RxChargeRequest(@NotNull UUID prescriptionId) {}
    public record ManualPaymentRequest(@NotBlank @Pattern(regexp = "cash|pos") String method,
                                       @NotNull @DecimalMin(value = "0.0", inclusive = false) BigDecimal amount) {}
    public record VoidRequest(@NotBlank String reason) {}
    public record InitPaymentRequest(@NotNull UUID invoiceId, String email) {}

    // FR-BIL-01
    @PostMapping("/invoices")
    public ResponseEntity<Map<String, Object>> create(@Valid @RequestBody CreateInvoiceRequest r,
                                                      HttpServletRequest req) {
        policy.authorize(req.getSession(false), "invoice.manage", PolicyContext.none());
        var inv = billing.createInvoice(r.patientId(), r.visitRef());
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("invoiceId", inv.getInvoiceId()));
    }

    // FR-BIL-02 (manual line)
    @PostMapping("/invoices/{id}/items")
    public ResponseEntity<Map<String, Object>> addItem(@PathVariable UUID id,
                                                       @Valid @RequestBody AddItemRequest r,
                                                       HttpServletRequest req) {
        policy.authorize(req.getSession(false), "invoice.manage", PolicyContext.none());
        var item = billing.addItem(id, r.sourceType(), r.sourceId(), r.description(), r.amount());
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("itemId", item.getItemId()));
    }

    // FR-BIL-02 (consultation fee)
    @PostMapping("/invoices/{id}/charges/consultation")
    public ResponseEntity<Map<String, Object>> chargeConsultation(@PathVariable UUID id,
                                                                  @Valid @RequestBody ConsultChargeRequest r,
                                                                  HttpServletRequest req) {
        policy.authorize(req.getSession(false), "invoice.manage", PolicyContext.none());
        var item = billing.postConsultationCharge(id, r.consultationId());
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("itemId", item.getItemId()));
    }

    // FR-BIL-02 (dispensed medication)
    @PostMapping("/invoices/{id}/charges/prescription")
    public ResponseEntity<Map<String, Object>> chargeRx(@PathVariable UUID id,
                                                        @Valid @RequestBody RxChargeRequest r,
                                                        HttpServletRequest req) {
        policy.authorize(req.getSession(false), "invoice.manage", PolicyContext.none());
        var created = billing.postDispenseCharges(id, r.prescriptionId());
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("itemsPosted", created.size()));
    }

    @PostMapping("/invoices/{id}/issue")
    public Map<String, Object> issue(@PathVariable UUID id, HttpServletRequest req) {
        policy.authorize(req.getSession(false), "invoice.manage", PolicyContext.none());
        return Map.of("status", billing.issue(id).getStatus());
    }

    // FR-BIL-07 (management only per matrix)
    @PostMapping("/invoices/{id}/void")
    public Map<String, Object> voidInvoice(@PathVariable UUID id, @Valid @RequestBody VoidRequest r,
                                           HttpServletRequest req) {
        SessionUser mgr = policy.authorize(req.getSession(false), "invoice.void", PolicyContext.none());
        return Map.of("status", billing.voidInvoice(id, r.reason(), mgr).getStatus());
    }

    // FR-BIL-04 (cash point)
    @PostMapping("/invoices/{id}/payments")
    public ResponseEntity<Map<String, Object>> manualPayment(@PathVariable UUID id,
                                                             @Valid @RequestBody ManualPaymentRequest r,
                                                             HttpServletRequest req) {
        SessionUser clerk = policy.authorize(req.getSession(false), "payment.record", PolicyContext.none());
        var p = billing.recordManualPayment(id, r.method(), r.amount(), clerk);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
            "paymentId", p.getPaymentId(), "status", p.getStatus()));
    }

    // FR-BIL-06 (patient/family visibility scoped by policy)
    @GetMapping("/invoices/{id}")
    public Map<String, Object> detail(@PathVariable UUID id, HttpServletRequest req) {
        policy.authorize(req.getSession(false), "invoice.read",
            PolicyContext.grant(billing.patientOf(id), "billing"));
        return billing.invoiceDetail(id);
    }

    @GetMapping("/patients/{patientId}/invoices")
    public Map<String, Object> byPatient(@PathVariable UUID patientId, HttpServletRequest req) {
        policy.authorize(req.getSession(false), "invoice.read", PolicyContext.grant(patientId, "billing"));
        return Map.of("invoices", billing.patientInvoices(patientId));
    }

    // FR-BIL-03: online payment via the PaymentGateway port (DD-07)
    @PostMapping("/payments/init")
    public ResponseEntity<Map<String, Object>> init(@Valid @RequestBody InitPaymentRequest r,
                                                    HttpServletRequest req) {
        SessionUser payer = policy.authorize(req.getSession(false), "payment.pay_online",
            PolicyContext.grant(billing.patientOf(r.invoiceId()), "billing"));
        var out = billing.initOnlinePayment(r.invoiceId(), payer, r.email());
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
            "paymentId", out.paymentId(), "redirectUrl", out.redirectUrl() == null ? "" : out.redirectUrl()));
    }

    /**
     * ITC Transflow callback (Fig. 8; API Definition §2). Unauthenticated by design — the
     * gateway is not a session holder, and the body is treated as untrusted: crediting
     * requires the independent check-transaction-status round-trip (NFR-SEC-06).
     * Per the spec, this endpoint ALWAYS answers HTTP 200 with a JSON body, even on
     * failure, so ITC does not retry storms; unverifiable payments simply stay pending
     * and are settled by /payments/{id}/verify.
     */
    @PostMapping("/payments/callback")
    public Map<String, Object> callback(@RequestBody Map<String, Object> body) {
        try {
            String reference = gateway.extractReference(body);
            if (reference == null) return Map.of("received", true, "status", "ignored");
            Object status = billing.handleCallback(reference).get("status");
            return Map.of("received", true, "status", String.valueOf(status));
        } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger(BillingController.class)
                .warn("callback processing failed: {}", e.getMessage());
            return Map.of("received", true, "status", "pending");
        }
    }

    /** ITC spec §3: verify when no callback arrived within the expected window. */
    @PostMapping("/payments/{paymentId}/verify")
    public Map<String, Object> verify(@PathVariable UUID paymentId, HttpServletRequest req) {
        policy.authorize(req.getSession(false), "invoice.read",
            PolicyContext.grant(billing.patientOfPayment(paymentId), "billing"));
        return billing.verifyPayment(paymentId);
    }
}
