package com.medicore.admin;

import com.medicore.common.SessionUser;
import com.medicore.policy.PolicyContext;
import com.medicore.policy.PolicyService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/** Staff administration — sys_admin only, via the admin.users action (FR-ADM-01). */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminService admin;
    private final PolicyService policy;

    public AdminController(AdminService admin, PolicyService policy) {
        this.admin = admin; this.policy = policy;
    }

    public record CreateStaffRequest(
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Size(min = 8, max = 100) String password,
        @NotBlank @Size(max = 160) String fullName,
        @NotBlank String role,
        UUID departmentId,
        UUID wardId) {}

    @GetMapping("/staff")
    public Map<String, Object> list(HttpServletRequest req) {
        policy.authorize(req.getSession(false), "admin.users", PolicyContext.none());
        return Map.of("staff", admin.listStaff(), "roles", StaffRoles.CREATABLE);
    }

    @PostMapping("/staff")
    public ResponseEntity<Map<String, Object>> create(@Valid @RequestBody CreateStaffRequest r,
                                                      HttpServletRequest req) {
        SessionUser actor = policy.authorize(req.getSession(false), "admin.users", PolicyContext.none());
        UUID id = admin.createStaff(r.email(), r.password(), r.fullName(), r.role(),
            r.departmentId(), r.wardId(), actor.userId());
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("staffId", id));
    }

    @PostMapping("/staff/{staffId}/deactivate")
    public Map<String, Object> deactivate(@PathVariable UUID staffId, HttpServletRequest req) {
        SessionUser actor = policy.authorize(req.getSession(false), "admin.users", PolicyContext.none());
        admin.setActive(staffId, false, actor.userId());
        return Map.of("active", false);
    }

    @PostMapping("/staff/{staffId}/reactivate")
    public Map<String, Object> reactivate(@PathVariable UUID staffId, HttpServletRequest req) {
        SessionUser actor = policy.authorize(req.getSession(false), "admin.users", PolicyContext.none());
        admin.setActive(staffId, true, actor.userId());
        return Map.of("active", true);
    }

    @PostMapping("/staff/{staffId}/unlock")
    public Map<String, Object> unlock(@PathVariable UUID staffId, HttpServletRequest req) {
        SessionUser actor = policy.authorize(req.getSession(false), "admin.users", PolicyContext.none());
        admin.unlock(staffId, actor.userId());
        return Map.of("unlocked", true);
    }
}
