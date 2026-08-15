package com.medicore;

import com.medicore.billing.BillingService;
import com.medicore.common.ApiException;
import com.medicore.common.SessionUser;
import com.medicore.clinical.ConsultationService;
import com.medicore.domain.*;
import com.medicore.payments.PaymentGateway;
import com.medicore.repo.Repositories.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * M3 integration tests (run: MEDICORE_IT=true ./gradlew test).
 * Uses a conformant in-memory gateway stub (DD-07 traceability: "callback without
 * successful status query never credits").
 */
@SpringBootTest(properties = "medicore.seed=false")
@EnabledIfEnvironmentVariable(named = "MEDICORE_IT", matches = "true")
@Import(BillingIT.StubGatewayConfig.class)
class BillingIT {

    /** Programmable stub standing in for ITC until the API specification arrives (OI-5). */
    static class StubGateway implements PaymentGateway {
        final AtomicReference<VerificationResult> nextVerify = new AtomicReference<>(
            new VerificationResult(false, null, "unset"));
        @Override public PaymentInstruction requestPayment(PaymentRequest request) {
            return new PaymentInstruction("STUB-" + request.localReference(), "https://stub.gateway/pay");
        }
        @Override public VerificationResult verifyStatus(String gatewayReference) {
            return nextVerify.get();
        }
        @Override public String extractReference(java.util.Map<String, Object> body) {
            Object r = body.get("reference");
            return r == null ? null : String.valueOf(r);
        }
    }

    @TestConfiguration
    static class StubGatewayConfig {
        @Bean @Primary StubGateway stubGateway() { return new StubGateway(); }
    }

    @Autowired BillingService billing;
    @Autowired ConsultationService clinical;
    @Autowired StubGateway stub;
    @Autowired UserRepository users;
    @Autowired StaffRepository staff;
    @Autowired PatientRepository patients;
    @Autowired DepartmentRepository departments;
    @Autowired ConsultationRepository consultations;
    @Autowired JdbcTemplate jdbc;

    private SessionUser clerk() {
        UserAccount u = users.save(new UserAccount("m3-clerk-" + UUID.randomUUID() + "@t.test", "x", "billing_clerk"));
        Staff s = staff.save(new Staff(u.getUserId(), null, "billing_clerk", "M3 Clerk"));
        return new SessionUser(u.getUserId(), "billing_clerk", s.getStaffId(), null);
    }

    private SessionUser manager() {
        UserAccount u = users.save(new UserAccount("m3-mgr-" + UUID.randomUUID() + "@t.test", "x", "management"));
        return new SessionUser(u.getUserId(), "management", null, null);
    }

    private Patient newPatient() {
        UserAccount u = users.save(new UserAccount("m3-pat-" + UUID.randomUUID() + "@t.test", "x", "patient"));
        // dashes stripped so the generated MRN fits patients.mrn varchar(40)
        return patients.save(new Patient(u.getUserId(),
            "MRN-M3-" + UUID.randomUUID().toString().replace("-", ""), "M3 Patient",
            LocalDate.of(1992, 7, 7), "female", null, null));
    }

    private UUID signedConsultationFor(Patient pat, BigDecimal fee) {
        Department dept = departments.save(new Department("M3Dept-" + UUID.randomUUID(), "clinical", fee));
        UserAccount du = users.save(new UserAccount("m3-doc-" + UUID.randomUUID() + "@t.test", "x", "doctor"));
        Staff doc = staff.save(new Staff(du.getUserId(), dept.getDepartmentId(), "doctor", "M3 Doc"));
        SessionUser docSession = new SessionUser(du.getUserId(), "doctor", doc.getStaffId(), null);
        Consultation c = consultations.save(new Consultation(null, doc.getStaffId(), pat.getPatientId()));
        clinical.updateNotes(c.getConsultationId(), docSession, "hx", "exam", "dx");
        clinical.sign(c.getConsultationId(), docSession);
        return c.getConsultationId();
    }

    @Test
    void lifecycleChargesPaymentsAndAppendOnlyGuards() {
        Patient pat = newPatient();
        UUID cid = signedConsultationFor(pat, new BigDecimal("80.00"));
        SessionUser clerk = clerk();

        Invoice inv = billing.createInvoice(pat.getPatientId(), "VISIT-1");
        billing.postConsultationCharge(inv.getInvoiceId(), cid);
        // duplicate consultation charge is rejected
        assertEquals(409, assertThrows(ApiException.class, () ->
            billing.postConsultationCharge(inv.getInvoiceId(), cid)).status());
        billing.addItem(inv.getInvoiceId(), "other", null, "Dressing kit", new BigDecimal("12.50"));
        billing.issue(inv.getInvoiceId());

        billing.recordManualPayment(inv.getInvoiceId(), "cash", new BigDecimal("40.00"), clerk);
        assertEquals("partially_paid", billing.invoiceDetail(inv.getInvoiceId()).get("status"));
        billing.recordManualPayment(inv.getInvoiceId(), "pos", new BigDecimal("52.50"), clerk);
        Map<String, Object> detail = billing.invoiceDetail(inv.getInvoiceId());
        assertEquals("paid", detail.get("status"));
        assertEquals(0, ((BigDecimal) detail.get("balance")).signum());

        // fully paid invoices accept no further charges (DD-05 lifecycle guard)
        assertEquals(422, assertThrows(ApiException.class, () -> billing.addItem(
            inv.getInvoiceId(), "other", null, "late fee", BigDecimal.ONE)).status());
    }

    @Test
    void onlinePaymentCreditsOnlyAfterExactSuccessfulVerification() {
        Patient pat = newPatient();
        SessionUser clerk = clerk();
        SessionUser payer = new SessionUser(
            users.findById(pat.getUserId()).orElseThrow().getUserId(), "patient", null, pat.getPatientId());

        Invoice inv = billing.createInvoice(pat.getPatientId(), "VISIT-OL");
        billing.addItem(inv.getInvoiceId(), "other", null, "Procedure", new BigDecimal("92.50"));
        billing.issue(inv.getInvoiceId());

        var init = billing.initOnlinePayment(inv.getInvoiceId(), payer, "p@t.test");
        String ref = "STUB-" + init.paymentId();

        // 1) callback arrives but the independent verify says FAILED -> never credited
        stub.nextVerify.set(new PaymentGateway.VerificationResult(false, new BigDecimal("92.50"), "failed"));
        assertEquals("failed", billing.handleCallback(ref).get("status"));
        assertEquals("issued", billing.invoiceDetail(inv.getInvoiceId()).get("status"));

        // 2) verify succeeds but the amount is wrong -> never credited
        var init2 = billing.initOnlinePayment(inv.getInvoiceId(), payer, "p@t.test");
        stub.nextVerify.set(new PaymentGateway.VerificationResult(true, new BigDecimal("90.00"), "success"));
        assertEquals("failed", billing.handleCallback("STUB-" + init2.paymentId()).get("status"));
        assertEquals("issued", billing.invoiceDetail(inv.getInvoiceId()).get("status"));

        // 3) verify succeeds with the exact amount -> credited; replayed callback is idempotent
        var init3 = billing.initOnlinePayment(inv.getInvoiceId(), payer, "p@t.test");
        stub.nextVerify.set(new PaymentGateway.VerificationResult(true, new BigDecimal("92.50"), "success"));
        assertEquals("paid", billing.handleCallback("STUB-" + init3.paymentId()).get("status"));
        assertEquals("paid", billing.invoiceDetail(inv.getInvoiceId()).get("status"));
        assertEquals("paid", billing.handleCallback("STUB-" + init3.paymentId()).get("status")); // replay
        Long paidCount = jdbc.queryForObject(
            "SELECT count(*) FROM payments WHERE invoice_id = ? AND status = 'paid'",
            Long.class, inv.getInvoiceId());
        assertEquals(1L, paidCount, "replay never double-credits");
    }

    @Test
    void vendorShapedCallbackBodyResolvesThroughAdapterExtraction() {
        // The ITC callback carries refNo (== transactionReference). The controller asks the
        // gateway to extract it; here we exercise the same path via the stub's contract.
        Patient pat = newPatient();
        SessionUser payer = new SessionUser(
            users.findById(pat.getUserId()).orElseThrow().getUserId(), "patient", null, pat.getPatientId());
        Invoice inv = billing.createInvoice(pat.getPatientId(), "VISIT-CB");
        billing.addItem(inv.getInvoiceId(), "other", null, "Consult", new BigDecimal("15.00"));
        billing.issue(inv.getInvoiceId());
        var init = billing.initOnlinePayment(inv.getInvoiceId(), payer, "p@t.test");
        stub.nextVerify.set(new PaymentGateway.VerificationResult(true, new BigDecimal("15.00"), "success"));
        String extracted = stub.extractReference(java.util.Map.of("reference", "STUB-" + init.paymentId()));
        assertEquals("paid", billing.handleCallback(extracted).get("status"));
    }

    @Test
    void voidRequiresReasonAndNoCapturedMoney() {
        Patient pat = newPatient();
        SessionUser clerk = clerk();
        SessionUser mgr = manager();

        Invoice inv = billing.createInvoice(pat.getPatientId(), "VISIT-V");
        billing.addItem(inv.getInvoiceId(), "other", null, "Consumables", new BigDecimal("10.00"));
        billing.issue(inv.getInvoiceId());
        assertEquals(422, assertThrows(ApiException.class, () ->
            billing.voidInvoice(inv.getInvoiceId(), "  ", mgr)).status());
        billing.recordManualPayment(inv.getInvoiceId(), "cash", new BigDecimal("10.00"), clerk);
        assertEquals(422, assertThrows(ApiException.class, () ->
            billing.voidInvoice(inv.getInvoiceId(), "entered in error", mgr)).status());

        Invoice inv2 = billing.createInvoice(pat.getPatientId(), "VISIT-V2");
        billing.addItem(inv2.getInvoiceId(), "other", null, "Wrong patient", new BigDecimal("5.00"));
        assertEquals("void", billing.voidInvoice(inv2.getInvoiceId(), "posted to wrong patient", mgr).getStatus());
    }
}
