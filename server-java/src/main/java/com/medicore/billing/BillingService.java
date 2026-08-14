package com.medicore.billing;

import com.medicore.audit.AuditService;
import com.medicore.common.ApiException;
import com.medicore.common.SessionUser;
import com.medicore.domain.Invoice;
import com.medicore.domain.InvoiceItem;
import com.medicore.domain.Payment;
import com.medicore.payments.PaymentGateway;
import com.medicore.repo.Repositories.InvoiceItemRepository;
import com.medicore.repo.Repositories.InvoiceRepository;
import com.medicore.repo.Repositories.PaymentRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Billing domain (FR-BIL-01..07). Invoice items are append-only (DD-05); totals are always
 * computed from the durable record. Online payments go through the PaymentGateway port
 * (DD-07) and are credited ONLY by PaymentVerifier's verify-before-credit rule (NFR-SEC-06).
 */
@Service
public class BillingService {

    private final InvoiceRepository invoices;
    private final InvoiceItemRepository items;
    private final PaymentRepository payments;
    private final PaymentGateway gateway;
    private final JdbcTemplate jdbc;
    private final AuditService audit;

    public BillingService(InvoiceRepository invoices, InvoiceItemRepository items,
                          PaymentRepository payments, PaymentGateway gateway,
                          JdbcTemplate jdbc, AuditService audit) {
        this.invoices = invoices; this.items = items; this.payments = payments;
        this.gateway = gateway; this.jdbc = jdbc; this.audit = audit;
    }

    // ---------- Invoice lifecycle (FR-BIL-01/05/07) ----------

    @Transactional
    public Invoice createInvoice(UUID patientId, String visitRef) {
        Long exists = jdbc.queryForObject("SELECT count(*) FROM patients WHERE patient_id = ?",
            Long.class, patientId);
        if (exists == null || exists == 0) throw new ApiException(404, "Patient not found");
        return invoices.save(new Invoice(patientId, visitRef));
    }

    @Transactional
    public Invoice issue(UUID invoiceId) {
        Invoice inv = require(invoiceId);
        if (!"draft".equals(inv.getStatus())) throw new ApiException(422, "Only draft invoices can be issued");
        if (total(invoiceId).signum() <= 0) throw new ApiException(422, "Cannot issue an empty invoice");
        inv.setStatus("issued");
        inv.setIssuedAt(Instant.now());
        return invoices.save(inv);
    }

    /** FR-BIL-07: management-only void with mandatory reason; blocked once money has been taken. */
    @Transactional
    public Invoice voidInvoice(UUID invoiceId, String reason, SessionUser manager) {
        Invoice inv = require(invoiceId);
        if ("void".equals(inv.getStatus())) throw new ApiException(422, "Already void");
        Long paidCount = jdbc.queryForObject(
            "SELECT count(*) FROM payments WHERE invoice_id = ? AND status = 'paid'", Long.class, invoiceId);
        if (paidCount != null && paidCount > 0)
            throw new ApiException(422, "Invoice has captured payments; refunds are a Phase-2 flow, not void");
        if (reason == null || reason.isBlank()) throw new ApiException(422, "A void reason is required");
        inv.setStatus("void");
        inv.setVoidReason(reason.trim());
        invoices.save(inv);
        audit.log(manager.userId(), inv.getPatientId(), "invoice.void", "invoices:" + invoiceId,
            "{\"reason\":\"" + reason.trim().replace("\"", "'") + "\"}");
        return inv;
    }

    // ---------- Charge posting (FR-BIL-02, DD-05: append-only) ----------

    @Transactional
    public InvoiceItem addItem(UUID invoiceId, String sourceType, UUID sourceId,
                               String description, BigDecimal amount) {
        Invoice inv = require(invoiceId);
        if (!"draft".equals(inv.getStatus()) && !"issued".equals(inv.getStatus())
            && !"partially_paid".equals(inv.getStatus()))
            throw new ApiException(422, "Cannot post charges to a " + inv.getStatus() + " invoice");
        if (amount == null || amount.signum() < 0) throw new ApiException(422, "Amount must be >= 0");
        InvoiceItem item = items.save(new InvoiceItem(invoiceId, sourceType, sourceId, description, amount));
        refreshStatus(inv); // a new charge can move paid-in-full back to partially_paid
        return item;
    }

    /** Posts the department consult fee for a signed consultation. Idempotent by source_id. */
    @Transactional
    public InvoiceItem postConsultationCharge(UUID invoiceId, UUID consultationId) {
        Map<String, Object> row = jdbc.queryForList("""
            SELECT c.signed_at, d.name AS dept, d.consult_fee
            FROM consultations c
            JOIN staff s ON s.staff_id = c.doctor_id
            JOIN departments d ON d.department_id = s.department_id
            WHERE c.consultation_id = ?
            """, consultationId).stream().findFirst()
            .orElseThrow(() -> new ApiException(404, "Consultation not found (or doctor has no department)"));
        if (row.get("signed_at") == null)
            throw new ApiException(422, "Consultation must be signed before charging");
        guardDuplicate(invoiceId, "consultation", consultationId);
        BigDecimal fee = (BigDecimal) row.get("consult_fee");
        return addItem(invoiceId, "consultation", consultationId,
            "Consultation - " + row.get("dept"), fee);
    }

    /** Posts dispensed medication charges for a prescription (qty x unit price per drug). Idempotent. */
    @Transactional
    public List<InvoiceItem> postDispenseCharges(UUID invoiceId, UUID prescriptionId) {
        guardDuplicate(invoiceId, "pharmacy", prescriptionId);
        List<Map<String, Object>> lines = jdbc.queryForList("""
            SELECT d.generic_name, d.strength, d.unit_price, sum(ds.qty) AS qty
            FROM dispenses ds
            JOIN prescription_items i ON i.rx_item_id = ds.rx_item_id
            JOIN drugs d ON d.drug_id = i.drug_id
            WHERE i.prescription_id = ?
            GROUP BY d.generic_name, d.strength, d.unit_price
            """, prescriptionId);
        if (lines.isEmpty()) throw new ApiException(422, "Nothing dispensed on this prescription yet");
        return lines.stream().map(l -> {
            BigDecimal qty = new BigDecimal(((Number) l.get("qty")).longValue());
            BigDecimal amount = ((BigDecimal) l.get("unit_price")).multiply(qty);
            return addItem(invoiceId, "pharmacy", prescriptionId,
                l.get("generic_name") + " " + l.get("strength") + " x" + qty, amount);
        }).toList();
    }

    // ---------- Payments (FR-BIL-03/04, NFR-SEC-06) ----------

    /** Manual capture at the cash point (cash/POS) by a billing clerk. */
    @Transactional
    public Payment recordManualPayment(UUID invoiceId, String method, BigDecimal amount, SessionUser clerk) {
        Invoice inv = require(invoiceId);
        requirePayable(inv);
        if (!"cash".equals(method) && !"pos".equals(method))
            throw new ApiException(422, "Manual capture is cash or pos only");
        if (amount == null || amount.signum() <= 0) throw new ApiException(422, "Amount must be positive");
        Payment p = new Payment(invoiceId, method, amount);
        p.setStatus("paid");
        p.setPaidAt(Instant.now());
        payments.save(p);
        refreshStatus(inv);
        audit.log(clerk.userId(), inv.getPatientId(), "payment.record",
            "payments:" + p.getPaymentId(), "{\"method\":\"" + method + "\",\"amount\":" + amount + "}");
        return p;
    }

    public record InitResult(UUID paymentId, String redirectUrl) {}

    /** Initialise an online payment for the outstanding balance through the gateway port. */
    @Transactional
    public InitResult initOnlinePayment(UUID invoiceId, SessionUser payer, String customerEmail) {
        Invoice inv = require(invoiceId);
        requirePayable(inv);
        BigDecimal balance = BillingMath.balance(total(invoiceId), paidSum(invoiceId));
        if (balance.signum() <= 0) throw new ApiException(422, "Nothing outstanding on this invoice");

        Payment p = new Payment(invoiceId, "itc", balance);
        payments.save(p); // pending

        Map<String, Object> contact = jdbc.queryForList("""
            SELECT pt.full_name, u.email FROM patients pt
            LEFT JOIN users u ON u.user_id = pt.user_id
            WHERE pt.patient_id = ?
            """, inv.getPatientId()).stream().findFirst().orElse(Map.of());
        String name = (String) contact.get("full_name");
        String email = (customerEmail != null && !customerEmail.isBlank())
            ? customerEmail : (String) contact.get("email");

        PaymentGateway.PaymentInstruction instruction = gateway.requestPayment(
            new PaymentGateway.PaymentRequest(invoiceId, p.getPaymentId().toString(), balance, name, email));
        p.setGatewayRef(instruction.gatewayReference());
        payments.save(p);
        audit.log(payer.userId(), inv.getPatientId(), "payment.init",
            "payments:" + p.getPaymentId(), "{\"amount\":" + balance + "}");
        return new InitResult(p.getPaymentId(), instruction.redirectUrl());
    }

    /**
     * Callback entry (Fig. 8). The reference identifies OUR payment row; everything else in
     * the callback is untrusted. Credit happens only after an independent verifyStatus call
     * passes PaymentVerifier (NFR-SEC-06). Idempotent for replayed callbacks.
     */
    @Transactional
    public Map<String, Object> handleCallback(String reference) {
        return verifyInternal(findByReference(reference));
    }

    /** §3 of the ITC spec: re-verify when a callback never arrived. Same credit rule. */
    @Transactional
    public Map<String, Object> verifyPayment(UUID paymentId) {
        return verifyInternal(payments.findById(paymentId)
            .orElseThrow(() -> new ApiException(404, "Payment not found")));
    }

    public UUID patientOfPayment(UUID paymentId) {
        Payment p = payments.findById(paymentId)
            .orElseThrow(() -> new ApiException(404, "Payment not found"));
        return require(p.getInvoiceId()).getPatientId();
    }

    private Map<String, Object> verifyInternal(Payment p) {
        if ("paid".equals(p.getStatus()))
            return Map.of("status", "paid", "note", "already credited (replay ignored)");

        PaymentGateway.VerificationResult vr;
        try {
            vr = gateway.verifyStatus(p.getGatewayRef() != null ? p.getGatewayRef() : reference);
        } catch (ApiException e) {
            throw e; // 501 while the ITC adapter awaits its specification (OI-5)
        }
        PaymentVerifier.Outcome outcome = PaymentVerifier.evaluate(vr, p.getAmount());

        Invoice inv = require(p.getInvoiceId());
        if (outcome.credit()) {
            p.setStatus("paid");
            p.setPaidAt(Instant.now());
            payments.save(p);
            refreshStatus(inv);
            audit.log(null, inv.getPatientId(), "payment.verified", "payments:" + p.getPaymentId(), null);
            return Map.of("status", "paid");
        } else {
            p.setStatus("failed");
            payments.save(p);
            audit.log(null, inv.getPatientId(), "payment.rejected", "payments:" + p.getPaymentId(),
                "{\"reason\":\"" + outcome.reason().replace("\"", "'") + "\"}");
            return Map.of("status", "failed");
        }
    }

    private Payment findByReference(String reference) {
        // accept either the gateway's reference or our own payment UUID as the local reference
        var byRef = payments.findByGatewayRef(reference);
        if (byRef.isPresent()) return byRef.get();
        try {
            return payments.findById(UUID.fromString(reference))
                .orElseThrow(() -> new ApiException(404, "Unknown payment reference"));
        } catch (IllegalArgumentException e) {
            throw new ApiException(404, "Unknown payment reference");
        }
    }

    // ---------- Read models ----------

    public Map<String, Object> invoiceDetail(UUID invoiceId) {
        Invoice inv = require(invoiceId);
        List<Map<String, Object>> itemRows = jdbc.queryForList(
            "SELECT item_id, source_type, description, amount, posted_at FROM invoice_items " +
            "WHERE invoice_id = ? ORDER BY posted_at", invoiceId);
        List<Map<String, Object>> payRows = jdbc.queryForList(
            "SELECT payment_id, method, amount, status, paid_at FROM payments " +
            "WHERE invoice_id = ? ORDER BY paid_at NULLS LAST", invoiceId);
        BigDecimal total = total(invoiceId);
        BigDecimal paid = paidSum(invoiceId);
        return Map.of("invoiceId", invoiceId, "patientId", inv.getPatientId(), "status", inv.getStatus(),
            "items", itemRows, "payments", payRows,
            "total", total, "paid", paid, "balance", BillingMath.balance(total, paid));
    }

    public List<Map<String, Object>> patientInvoices(UUID patientId) {
        return jdbc.queryForList("""
            SELECT i.invoice_id, i.status, i.visit_ref, i.created_at,
                   COALESCE((SELECT sum(amount) FROM invoice_items WHERE invoice_id = i.invoice_id), 0) AS total,
                   COALESCE((SELECT sum(amount) FROM payments WHERE invoice_id = i.invoice_id AND status = 'paid'), 0) AS paid
            FROM invoices i WHERE i.patient_id = ? ORDER BY i.created_at DESC
            """, patientId);
    }

    public UUID patientOf(UUID invoiceId) { return require(invoiceId).getPatientId(); }

    /** Chargeable sources for the billing workspace: signed consultations and dispensed prescriptions. */
    public Map<String, Object> patientBillables(UUID patientId) {
        List<Map<String, Object>> consults = jdbc.queryForList("""
            SELECT c.consultation_id, c.signed_at, d.name AS department, d.consult_fee,
                   s.full_name AS doctor
            FROM consultations c
            JOIN staff s ON s.staff_id = c.doctor_id
            JOIN departments d ON d.department_id = s.department_id
            WHERE c.patient_id = ? AND c.signed_at IS NOT NULL
            ORDER BY c.signed_at DESC LIMIT 50
            """, patientId);
        List<Map<String, Object>> rx = jdbc.queryForList("""
            SELECT p.prescription_id, p.status, p.created_at,
                   COALESCE(sum(ds.qty * d.unit_price), 0) AS dispensed_value
            FROM prescriptions p
            JOIN prescription_items i ON i.prescription_id = p.prescription_id
            JOIN drugs d ON d.drug_id = i.drug_id
            LEFT JOIN dispenses ds ON ds.rx_item_id = i.rx_item_id
            WHERE p.patient_id = ?
            GROUP BY p.prescription_id, p.status, p.created_at
            HAVING COALESCE(sum(ds.qty), 0) > 0
            ORDER BY p.created_at DESC LIMIT 50
            """, patientId);
        return Map.of("consultations", consults, "prescriptions", rx);
    }

    // ---------- internals ----------

    private Invoice require(UUID invoiceId) {
        return invoices.findById(invoiceId).orElseThrow(() -> new ApiException(404, "Invoice not found"));
    }

    private void requirePayable(Invoice inv) {
        if (!"issued".equals(inv.getStatus()) && !"partially_paid".equals(inv.getStatus()))
            throw new ApiException(422, "Invoice is not payable (status: " + inv.getStatus() + ")");
    }

    private void guardDuplicate(UUID invoiceId, String sourceType, UUID sourceId) {
        Long n = jdbc.queryForObject(
            "SELECT count(*) FROM invoice_items WHERE invoice_id = ? AND source_type = ? AND source_id = ?",
            Long.class, invoiceId, sourceType, sourceId);
        if (n != null && n > 0) throw new ApiException(409, "Charge already posted for this source");
    }

    private BigDecimal total(UUID invoiceId) {
        BigDecimal t = jdbc.queryForObject(
            "SELECT COALESCE(sum(amount), 0) FROM invoice_items WHERE invoice_id = ?", BigDecimal.class, invoiceId);
        return t == null ? BigDecimal.ZERO : t;
    }

    private BigDecimal paidSum(UUID invoiceId) {
        BigDecimal p = jdbc.queryForObject(
            "SELECT COALESCE(sum(amount), 0) FROM payments WHERE invoice_id = ? AND status = 'paid'",
            BigDecimal.class, invoiceId);
        return p == null ? BigDecimal.ZERO : p;
    }

    /** Recompute a live invoice's derived status from the durable record (FR-BIL-05). */
    private void refreshStatus(Invoice inv) {
        if ("draft".equals(inv.getStatus()) || "void".equals(inv.getStatus())) return;
        inv.setStatus(BillingMath.deriveStatus(total(inv.getInvoiceId()), paidSum(inv.getInvoiceId())));
        invoices.save(inv);
    }
}
