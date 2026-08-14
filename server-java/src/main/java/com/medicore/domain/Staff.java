package com.medicore.domain;

import jakarta.persistence.*;
import java.util.UUID;

@Entity @Table(name = "staff")
public class Staff {
    @Id @Column(name = "staff_id") private UUID staffId = UUID.randomUUID();
    @Column(name = "user_id", nullable = false, unique = true) private UUID userId;
    @Column(name = "department_id") private UUID departmentId;
    @Column(name = "staff_type", nullable = false) private String staffType;
    @Column(name = "full_name", nullable = false) private String fullName;
    @Column(name = "assigned_ward_id") private UUID assignedWardId; // AC-03

    protected Staff() {}
    public Staff(UUID userId, UUID departmentId, String staffType, String fullName) {
        this.userId = userId; this.departmentId = departmentId; this.staffType = staffType; this.fullName = fullName;
    }
    public UUID getStaffId() { return staffId; }
    public UUID getUserId() { return userId; }
    public UUID getDepartmentId() { return departmentId; }
    public String getFullName() { return fullName; }
}
