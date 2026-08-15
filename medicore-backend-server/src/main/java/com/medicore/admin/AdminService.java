package com.medicore.admin;

import com.medicore.audit.AuditService;
import com.medicore.common.ApiException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Staff administration (FR-ADM-01). StaffRoles owns the rules; this is the persistence shell. */
@Service
public class AdminService {

    private final JdbcTemplate jdbc;
    private final PasswordEncoder encoder;
    private final AuditService audit;

    public AdminService(JdbcTemplate jdbc, PasswordEncoder encoder, AuditService audit) {
        this.jdbc = jdbc; this.encoder = encoder; this.audit = audit;
    }

    /** The roster an administrator manages: who exists, what they are, and whether they can sign in. */
    public List<Map<String, Object>> listStaff() {
        return jdbc.queryForList("""
            SELECT s.staff_id, s.full_name, s.staff_type, u.email, u.role, u.is_active,
                   u.locked_until IS NOT NULL AND u.locked_until > now() AS locked,
                   d.name AS department, w.name AS ward, u.created_at
            FROM staff s
            JOIN users u ON u.user_id = s.user_id
            LEFT JOIN departments d ON d.department_id = s.department_id
            LEFT JOIN wards w ON w.ward_id = s.assigned_ward_id
            ORDER BY u.is_active DESC, s.staff_type, s.full_name
            """);
    }

    @Transactional
    public UUID createStaff(String email, String rawPassword, String fullName, String role,
                            UUID departmentId, UUID wardId, UUID actorUserId) {
        String problem = StaffRoles.validate(role, departmentId != null, wardId != null);
        if (problem != null) throw new ApiException(422, problem);

        // A department is meaningful for any role; a ward only scopes nursing access, so
        // storing one against a non-nurse would imply a permission it does not grant.
        UUID dept = departmentId;
        UUID ward = StaffRoles.requiresWard(role) ? wardId : null;

        if (dept != null && !exists("SELECT 1 FROM departments WHERE department_id = ?", dept))
            throw new ApiException(422, "That department does not exist");
        if (ward != null && !exists("SELECT 1 FROM wards WHERE ward_id = ?", ward))
            throw new ApiException(422, "That ward does not exist");

        UUID userId = UUID.randomUUID();
        UUID staffId = UUID.randomUUID();
        try {
            jdbc.update("INSERT INTO users (user_id, email, password_hash, role) VALUES (?,?,?,?)",
                userId, email.trim().toLowerCase(), encoder.encode(rawPassword), role);
        } catch (DuplicateKeyException e) {
            throw new ApiException(409, "That email address is already registered");
        }
        jdbc.update("""
            INSERT INTO staff (staff_id, user_id, department_id, staff_type, full_name, assigned_ward_id)
            VALUES (?,?,?,?,?,?)
            """, staffId, userId, dept, StaffRoles.staffTypeFor(role), fullName.trim(), ward);

        audit.log(actorUserId, null, "admin.users", "staff:" + staffId,
            "{\"action\":\"create\",\"role\":\"" + role + "\"}");
        return staffId;
    }

    /**
     * Leavers are deactivated, never deleted: audit_log references the user row and is
     * append-only (NFR-SEC-04), so the identity behind a recorded action always resolves.
     * AuthService refuses login for an inactive account.
     */
    @Transactional
    public void setActive(UUID staffId, boolean active, UUID actorUserId) {
        Map<String, Object> row = jdbc.queryForList(
            "SELECT u.user_id, u.email FROM staff s JOIN users u ON u.user_id = s.user_id WHERE s.staff_id = ?",
            staffId).stream().findFirst().orElseThrow(() -> new ApiException(404, "Staff member not found"));
        UUID userId = (UUID) row.get("user_id");

        if (!active && userId.equals(actorUserId))
            throw new ApiException(422, "You cannot deactivate your own account");

        jdbc.update("UPDATE users SET is_active = ?, failed_logins = 0, locked_until = NULL, updated_at = now() "
                  + "WHERE user_id = ?", active, userId);
        audit.log(actorUserId, null, "admin.users", "staff:" + staffId,
            "{\"action\":\"" + (active ? "reactivate" : "deactivate") + "\"}");
    }

    /** Clears a lockout after the five-failure rule has fired (FR-AUTH-06). */
    @Transactional
    public void unlock(UUID staffId, UUID actorUserId) {
        int updated = jdbc.update("""
            UPDATE users SET failed_logins = 0, locked_until = NULL, updated_at = now()
            WHERE user_id = (SELECT user_id FROM staff WHERE staff_id = ?)
            """, staffId);
        if (updated == 0) throw new ApiException(404, "Staff member not found");
        audit.log(actorUserId, null, "admin.users", "staff:" + staffId, "{\"action\":\"unlock\"}");
    }

    private boolean exists(String sql, Object... args) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(" + sql + ")", Boolean.class, args));
    }
}
