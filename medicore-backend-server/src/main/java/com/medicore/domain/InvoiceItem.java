package com.medicore.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;

/** DD-05: append-only. Rows are inserted, never updated or deleted; corrections go via void. */
@Entity @Table(name = "invoice_items")
public class InvoiceItem {
    @Id @Column(name = "item_id") private UUID itemId = UUID.randomUUID();
    @Column(name = "invoice_id", nullable = false) private UUID invoiceId;
    @Column(name = "source_type", nullable = false) private String sourceType; // FR-BIL-02
    @Column(name = "source_id") private UUID sourceId;
    @Column(nullable = false) private String description;
    @Column(nullable = false) private BigDecimal amount;

    protected InvoiceItem() {}
    public InvoiceItem(UUID invoiceId, String sourceType, UUID sourceId, String description, BigDecimal amount) {
        this.invoiceId = invoiceId; this.sourceType = sourceType; this.sourceId = sourceId;
        this.description = description; this.amount = amount;
    }
    public UUID getItemId() { return itemId; }
}
