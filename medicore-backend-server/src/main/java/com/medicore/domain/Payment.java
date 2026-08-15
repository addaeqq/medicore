package com.medicore.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name = "payments")
public class Payment {
    @Id @Column(name = "payment_id") private UUID paymentId = UUID.randomUUID();
    @Column(name = "invoice_id", nullable = false) private UUID invoiceId;
    @Column(nullable = false) private String method;            // itc|cash|pos (SRS v1.2)
    @Column(nullable = false) private BigDecimal amount;
    @Column(name = "gateway_ref", unique = true) private String gatewayRef; // NFR-SEC-06 verification key
    @Column(nullable = false) private String status = "pending";
    @Column(name = "paid_at") private Instant paidAt;

    protected Payment() {}
    public Payment(UUID invoiceId, String method, BigDecimal amount) {
        this.invoiceId = invoiceId; this.method = method; this.amount = amount;
    }
    public UUID getPaymentId() { return paymentId; }
    public UUID getInvoiceId() { return invoiceId; }
    public BigDecimal getAmount() { return amount; }
    public String getStatus() { return status; }
    public String getGatewayRef() { return gatewayRef; }
    public void setGatewayRef(String ref) { this.gatewayRef = ref; }
    public void setStatus(String status) { this.status = status; }
    public void setPaidAt(Instant t) { this.paidAt = t; }
}
