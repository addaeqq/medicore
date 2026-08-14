package com.medicore.domain;

import jakarta.persistence.*;
import java.util.UUID;

@Entity @Table(name = "allergies")
public class Allergy {
    @Id @Column(name = "allergy_id") private UUID allergyId = UUID.randomUUID();
    @Column(name = "patient_id", nullable = false) private UUID patientId;
    @Column(nullable = false) private String substance;
    @Column(nullable = false) private String severity;

    protected Allergy() {}
    public Allergy(UUID patientId, String substance, String severity) {
        this.patientId = patientId; this.substance = substance; this.severity = severity;
    }
    public UUID getAllergyId() { return allergyId; }
}
