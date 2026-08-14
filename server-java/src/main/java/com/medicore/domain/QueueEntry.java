package com.medicore.domain;

import jakarta.persistence.*;
import java.util.UUID;

@Entity @Table(name = "queue_entries")
public class QueueEntry {
    @Id @Column(name = "queue_entry_id") private UUID queueEntryId = UUID.randomUUID();
    @Column(name = "appointment_id", nullable = false, unique = true) private UUID appointmentId;
    @Column(nullable = false) private short priority = 100; // DD-06
    @Column(nullable = false) private String status = "waiting";

    protected QueueEntry() {}
    public QueueEntry(UUID appointmentId) { this.appointmentId = appointmentId; }
    public UUID getQueueEntryId() { return queueEntryId; }
}
