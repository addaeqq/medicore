package com.medicore.seed;

import com.medicore.domain.*;
import com.medicore.repo.Repositories.*;
import com.medicore.scheduling.SchedulingService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/** Demo seed (synthetic data only, NFR-PRV-03). Enable with MEDICORE_SEED=true. Idempotent. */
@Component
@ConditionalOnProperty(name = "medicore.seed", havingValue = "true")
public class SeedRunner implements CommandLineRunner {
    private final UserRepository users;
    private final StaffRepository staff;
    private final PatientRepository patients;
    private final DepartmentRepository departments;
    private final SchedulingService scheduling;
    private final PasswordEncoder encoder;
    private final JdbcTemplate jdbc;

    public SeedRunner(UserRepository users, StaffRepository staff, PatientRepository patients,
                      DepartmentRepository departments, SchedulingService scheduling,
                      PasswordEncoder encoder, JdbcTemplate jdbc) {
        this.users = users; this.staff = staff; this.patients = patients;
        this.departments = departments; this.scheduling = scheduling;
        this.encoder = encoder; this.jdbc = jdbc;
    }

    private UserAccount user(String email, String role) {
        return users.findByEmail(email).orElseGet(() ->
            users.save(new UserAccount(email, encoder.encode("Password123!"), role)));
    }

    private Staff staffMember(String email, String role, String type, String name, UUID deptId) {
        UserAccount u = user(email, role);
        return staff.findByUserId(u.getUserId()).orElseGet(() ->
            staff.save(new Staff(u.getUserId(), deptId, type, name)));
    }

    @Override
    public void run(String... args) {
        Department gm = departments.findByName("General Medicine").orElseGet(() ->
            departments.save(new Department("General Medicine", "clinical", new BigDecimal("80"))));
        departments.findByName("Pediatrics").orElseGet(() ->
            departments.save(new Department("Pediatrics", "clinical", new BigDecimal("70"))));

        Staff doctor = staffMember("doctor@medicore.test", "doctor", "doctor", "Dr. Abena Mensah", gm.getDepartmentId());
        staffMember("admin@medicore.test", "sys_admin", "sys_admin", "System Administrator", null);
        staffMember("reception@medicore.test", "receptionist", "receptionist", "Front Desk", gm.getDepartmentId());
        staffMember("pharmacist@medicore.test", "pharmacist", "pharmacist", "Pharm. Kojo Asante", null);
        staffMember("billing@medicore.test", "billing_clerk", "billing_clerk", "Cashier One", null);
        staffMember("management@medicore.test", "management", "management", "Hospital Manager", null);

        UserAccount pu = user("patient@medicore.test", "patient");
        patients.findByUserId(pu.getUserId()).orElseGet(() -> patients.save(new Patient(
            pu.getUserId(), "MRN-DEMO01", "Kwame Owusu",
            LocalDate.of(1990, 5, 14), "male", "+233200000000", null)));

        Long schedCount = jdbc.queryForObject(
            "SELECT count(*) FROM schedules WHERE doctor_id = ?", Long.class, doctor.getStaffId());
        if (schedCount != null && schedCount == 0) {
            for (int weekday : new int[]{1, 3, 5}) {
                scheduling.createScheduleWithSlots(doctor.getStaffId(), weekday,
                    LocalTime.of(9, 0), LocalTime.of(12, 0), 20, "C1", 28);
            }
        }
        System.out.println("Seed complete. Demo logins (password: Password123!): admin@ / doctor@ / " +
            "reception@ / pharmacist@ / billing@ / management@ / patient@medicore.test");
    }
}
