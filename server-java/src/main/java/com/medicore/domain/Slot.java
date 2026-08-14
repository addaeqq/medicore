package com.medicore.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name = "slots")
public class Slot {
    @Id @Column(name = "slot_id") private UUID slotId = UUID.randomUUID();
    @Column(name = "schedule_id", nullable = false) private UUID scheduleId;
    @Column(name = "doctor_id", nullable = false) private UUID doctorId;
    @Column(name = "starts_at", nullable = false) private Instant startsAt;
    @Column(name = "ends_at", nullable = false) private Instant endsAt;
    @Column(nullable = false) private String status = "available";

    protected Slot() {}
    public UUID getSlotId() { return slotId; }
    public UUID getDoctorId() { return doctorId; }
    public Instant getStartsAt() { return startsAt; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
