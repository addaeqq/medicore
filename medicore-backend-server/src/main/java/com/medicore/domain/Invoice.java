package com.medicore.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name = "invoices")
public class Invoice {
    @Id @Column(name = "invoice_id") private UUID invoiceId = UUID.randomUUID();
    @Column(name = "patient_id", nullable = false) private UUID patientId;
    @Column(name = "visit_ref") private String visitRef;
    @Column(nullable = false) private String status = "draft"; // draft|issued|partially_paid|paid|void
    @Column(name = "void_reason") private String voidReason;   // FR-BIL-07
    @Column(name = "issued_at") private Instant issuedAt;

    protected Invoice() {}
    public Invoice(UUID patientId, String visitRef) { this.patientId = patientId; this.visitRef = visitRef; }
    public UUID getInvoiceId() { return invoiceId; }
    public UUID getPatientId() { return patientId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public void setVoidReason(String r) { this.voidReason = r; }
    public void setIssuedAt(Instant t) { this.issuedAt = t; }
}
