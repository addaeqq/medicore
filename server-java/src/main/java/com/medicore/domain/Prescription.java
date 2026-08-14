package com.medicore.domain;

import jakarta.persistence.*;
import java.util.UUID;

@Entity @Table(name = "prescriptions")
public class Prescription {
    @Id @Column(name = "prescription_id") private UUID prescriptionId = UUID.randomUUID();
    @Column(name = "consultation_id", nullable = false) private UUID consultationId;
    @Column(name = "doctor_id", nullable = false) private UUID doctorId;
    @Column(name = "patient_id", nullable = false) private UUID patientId;
    @Column(nullable = false) private String status = "open"; // open|partially_dispensed|dispensed|cancelled

    protected Prescription() {}
    public Prescription(UUID consultationId, UUID doctorId, UUID patientId) {
        this.consultationId = consultationId; this.doctorId = doctorId; this.patientId = patientId;
    }
    public UUID getPrescriptionId() { return prescriptionId; }
    public UUID getPatientId() { return patientId; }
    public UUID getDoctorId() { return doctorId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
