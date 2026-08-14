package com.medicore.domain;

import jakarta.persistence.*;
import java.util.UUID;

@Entity @Table(name = "appointments")
public class Appointment {
    @Id @Column(name = "appointment_id") private UUID appointmentId = UUID.randomUUID();
    // DD-04 / FR-APT-04: UNIQUE(slot_id) in the schema is the double-booking guard.
    @Column(name = "slot_id", nullable = false, unique = true) private UUID slotId;
    @Column(name = "patient_id", nullable = false) private UUID patientId;
    @Column(name = "department_id", nullable = false) private UUID departmentId;
    @Column(nullable = false) private String status = "booked";

    protected Appointment() {}
    public Appointment(UUID slotId, UUID patientId, UUID departmentId) {
        this.slotId = slotId; this.patientId = patientId; this.departmentId = departmentId;
    }
    public UUID getAppointmentId() { return appointmentId; }
    public UUID getSlotId() { return slotId; }
    public UUID getPatientId() { return patientId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
