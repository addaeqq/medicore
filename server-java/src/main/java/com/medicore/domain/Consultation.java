package com.medicore.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name = "consultations")
public class Consultation {
    @Id @Column(name = "consultation_id") private UUID consultationId = UUID.randomUUID();
    @Column(name = "appointment_id") private UUID appointmentId;
    @Column(name = "admission_id") private UUID admissionId;
    @Column(name = "doctor_id", nullable = false) private UUID doctorId;
    @Column(name = "patient_id", nullable = false) private UUID patientId;
    private String complaint;
    private String findings;
    private String diagnosis;
    @Column(name = "signed_at") private Instant signedAt; // immutable once set (FR-EMR-03, V4 trigger)

    protected Consultation() {}
    public Consultation(UUID appointmentId, UUID doctorId, UUID patientId) {
        this.appointmentId = appointmentId; this.doctorId = doctorId; this.patientId = patientId;
    }
    public UUID getConsultationId() { return consultationId; }
    public UUID getAppointmentId() { return appointmentId; }
    public UUID getDoctorId() { return doctorId; }
    public UUID getPatientId() { return patientId; }
    public String getDiagnosis() { return diagnosis; }
    public Instant getSignedAt() { return signedAt; }
    public void setComplaint(String v) { this.complaint = v; }
    public void setFindings(String v) { this.findings = v; }
    public void setDiagnosis(String v) { this.diagnosis = v; }
    public void setSignedAt(Instant v) { this.signedAt = v; }
}
