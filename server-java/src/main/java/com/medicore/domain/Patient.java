package com.medicore.domain;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.util.UUID;

@Entity @Table(name = "patients")
public class Patient {
    @Id @Column(name = "patient_id") private UUID patientId = UUID.randomUUID();
    @Column(name = "user_id", unique = true) private UUID userId; // nullable: walk-ins (FR-PAT-02)
    @Column(nullable = false, unique = true) private String mrn;
    @Column(name = "full_name", nullable = false) private String fullName;
    @Column(nullable = false) private LocalDate dob;
    @Column(nullable = false) private String sex;
    private String phone;
    private String address;
    @Column(name = "next_of_kin") private String nextOfKin;

    protected Patient() {}
    public Patient(UUID userId, String mrn, String fullName, LocalDate dob, String sex, String phone, String address) {
        this.userId = userId; this.mrn = mrn; this.fullName = fullName;
        this.dob = dob; this.sex = sex; this.phone = phone; this.address = address;
    }
    public UUID getPatientId() { return patientId; }
    public UUID getUserId() { return userId; }
    public String getMrn() { return mrn; }
    public String getFullName() { return fullName; }
}
