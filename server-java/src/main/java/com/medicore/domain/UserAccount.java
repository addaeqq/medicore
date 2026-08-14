package com.medicore.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name = "users")
public class UserAccount {
    @Id @Column(name = "user_id") private UUID userId = UUID.randomUUID();
    @Column(nullable = false, unique = true) private String email;
    @Column(name = "password_hash", nullable = false) private String passwordHash; // bcrypt (FR-AUTH-02)
    @Column(nullable = false) private String role;
    @Column(name = "is_active", nullable = false) private boolean active = true;
    @Column(name = "failed_logins", nullable = false) private int failedLogins = 0; // FR-AUTH-06
    @Column(name = "locked_until") private Instant lockedUntil;

    protected UserAccount() {}
    public UserAccount(String email, String passwordHash, String role) {
        this.email = email; this.passwordHash = passwordHash; this.role = role;
    }
    public UUID getUserId() { return userId; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public String getRole() { return role; }
    public boolean isActive() { return active; }
    public int getFailedLogins() { return failedLogins; }
    public void setFailedLogins(int n) { this.failedLogins = n; }
    public Instant getLockedUntil() { return lockedUntil; }
    public void setLockedUntil(Instant t) { this.lockedUntil = t; }
}
