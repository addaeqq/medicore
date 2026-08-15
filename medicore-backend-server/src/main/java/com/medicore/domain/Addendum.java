package com.medicore.domain;

import jakarta.persistence.*;
import java.util.UUID;

@Entity @Table(name = "addendums")
public class Addendum {
    @Id @Column(name = "addendum_id") private UUID addendumId = UUID.randomUUID();
    @Column(name = "consultation_id", nullable = false) private UUID consultationId;
    @Column(name = "author_id", nullable = false) private UUID authorId;
    @Column(nullable = false) private String body;

    protected Addendum() {}
    public Addendum(UUID consultationId, UUID authorId, String body) {
        this.consultationId = consultationId; this.authorId = authorId; this.body = body;
    }
    public UUID getAddendumId() { return addendumId; }
}
