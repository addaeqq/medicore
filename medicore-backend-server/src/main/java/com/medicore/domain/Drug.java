package com.medicore.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;

@Entity @Table(name = "drugs")
public class Drug {
    @Id @Column(name = "drug_id") private UUID drugId = UUID.randomUUID();
    @Column(name = "generic_name", nullable = false) private String genericName;
    @Column(name = "brand_name") private String brandName;
    @Column(nullable = false) private String form;
    @Column(nullable = false) private String strength;
    @Column(name = "unit_price", nullable = false) private BigDecimal unitPrice;
    @Column(name = "reorder_level", nullable = false) private int reorderLevel = 10; // FR-PHM-06
    @Column(name = "is_controlled", nullable = false) private boolean controlled = false;

    protected Drug() {}
    public Drug(String genericName, String brandName, String form, String strength,
                BigDecimal unitPrice, int reorderLevel) {
        this.genericName = genericName; this.brandName = brandName; this.form = form;
        this.strength = strength; this.unitPrice = unitPrice; this.reorderLevel = reorderLevel;
    }
    public UUID getDrugId() { return drugId; }
    public String getGenericName() { return genericName; }
    public BigDecimal getUnitPrice() { return unitPrice; }
}
