package com.medicore.domain;

import jakarta.persistence.*;
import java.util.UUID;

@Entity @Table(name = "dispenses")
public class Dispense {
    @Id @Column(name = "dispense_id") private UUID dispenseId = UUID.randomUUID();
    @Column(name = "rx_item_id", nullable = false) private UUID rxItemId;
    @Column(name = "batch_id", nullable = false) private UUID batchId;
    @Column(nullable = false) private int qty;
    @Column(name = "dispensed_by", nullable = false) private UUID dispensedBy;

    protected Dispense() {}
    public Dispense(UUID rxItemId, UUID batchId, int qty, UUID dispensedBy) {
        this.rxItemId = rxItemId; this.batchId = batchId; this.qty = qty; this.dispensedBy = dispensedBy;
    }
}
