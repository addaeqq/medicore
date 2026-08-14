package com.medicore.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity @Table(name = "stock_batches")
public class StockBatch {
    @Id @Column(name = "batch_id") private UUID batchId = UUID.randomUUID();
    @Column(name = "drug_id", nullable = false) private UUID drugId;
    @Column(name = "batch_no", nullable = false) private String batchNo;
    @Column(name = "expiry_date", nullable = false) private LocalDate expiryDate;
    @Column(name = "qty_on_hand", nullable = false) private int qtyOnHand; // CHECK >= 0 in schema
    @Column(name = "unit_cost") private BigDecimal unitCost;

    protected StockBatch() {}
    public StockBatch(UUID drugId, String batchNo, LocalDate expiryDate, int qtyOnHand, BigDecimal unitCost) {
        this.drugId = drugId; this.batchNo = batchNo; this.expiryDate = expiryDate;
        this.qtyOnHand = qtyOnHand; this.unitCost = unitCost;
    }
    public UUID getBatchId() { return batchId; }
    public int getQtyOnHand() { return qtyOnHand; }
}
