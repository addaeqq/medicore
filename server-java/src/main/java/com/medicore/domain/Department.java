package com.medicore.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;

@Entity @Table(name = "departments")
public class Department {
    @Id @Column(name = "department_id") private UUID departmentId = UUID.randomUUID();
    @Column(nullable = false, unique = true) private String name;
    @Column(name = "dept_type", nullable = false) private String deptType;
    @Column(name = "consult_fee", nullable = false) private BigDecimal consultFee = BigDecimal.ZERO;

    protected Department() {}
    public Department(String name, String deptType, BigDecimal consultFee) {
        this.name = name; this.deptType = deptType; this.consultFee = consultFee;
    }
    public UUID getDepartmentId() { return departmentId; }
    public String getName() { return name; }
    public BigDecimal getConsultFee() { return consultFee; }
}
