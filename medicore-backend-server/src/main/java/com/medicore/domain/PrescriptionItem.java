package com.medicore.domain;

import jakarta.persistence.*;
import java.util.UUID;

@Entity @Table(name = "prescription_items")
public class PrescriptionItem {
    @Id @Column(name = "rx_item_id") private UUID rxItemId = UUID.randomUUID();
    @Column(name = "prescription_id", nullable = false) private UUID prescriptionId;
    @Column(name = "drug_id", nullable = false) private UUID drugId;
    @Column(nullable = false) private String dose;
    @Column(nullable = false) private String frequency;
    @Column(name = "duration_days") private Short durationDays;
    @Column(nullable = false) private int quantity; // CHECK > 0 in schema

    protected PrescriptionItem() {}
    public PrescriptionItem(UUID prescriptionId, UUID drugId, String dose, String frequency,
                            Short durationDays, int quantity) {
        this.prescriptionId = prescriptionId; this.drugId = drugId; this.dose = dose;
        this.frequency = frequency; this.durationDays = durationDays; this.quantity = quantity;
    }
    public UUID getRxItemId() { return rxItemId; }
    public UUID getDrugId() { return drugId; }
    public int getQuantity() { return quantity; }
    public UUID getPrescriptionId() { return prescriptionId; }
}
