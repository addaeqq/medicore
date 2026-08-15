package com.medicore.common;

import com.medicore.policy.PolicyContext;
import com.medicore.policy.PolicyService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** Read-only directory endpoints backing the web UI (added with the frontend milestone). */
@RestController
@RequestMapping("/api")
public class DirectoryController {
    private final JdbcTemplate jdbc;
    private final PolicyService policy;

    public DirectoryController(JdbcTemplate jdbc, PolicyService policy) {
        this.jdbc = jdbc; this.policy = policy;
    }

    @GetMapping("/departments")
    public Map<String, Object> departments(HttpServletRequest req) {
        policy.authorize(req.getSession(false), "directory.read", PolicyContext.none());
        return Map.of("departments", jdbc.queryForList(
            "SELECT department_id, name, dept_type, consult_fee FROM departments ORDER BY name"));
    }

    @GetMapping("/doctors")
    public Map<String, Object> doctors(HttpServletRequest req) {
        policy.authorize(req.getSession(false), "directory.read", PolicyContext.none());
        return Map.of("doctors", jdbc.queryForList("""
            SELECT s.staff_id, s.full_name, d.department_id, d.name AS department
            FROM staff s LEFT JOIN departments d ON d.department_id = s.department_id
            WHERE s.staff_type = 'doctor' ORDER BY s.full_name
            """));
    }

    /** Reception / billing / sys-admin patient lookup by name or MRN. */
    @GetMapping("/patients/search")
    public Map<String, Object> searchPatients(@RequestParam String q, HttpServletRequest req) {
        policy.authorize(req.getSession(false), "patient.read_profile", PolicyContext.none());
        String like = "%" + q.trim() + "%";
        return Map.of("patients", jdbc.queryForList("""
            SELECT patient_id, mrn, full_name, dob, sex, phone
            FROM patients WHERE full_name ILIKE ? OR mrn ILIKE ?
            ORDER BY full_name LIMIT 20
            """, like, like));
    }

    /** The signed-in user's own context: role plus staff/patient details for the dashboard. */
    @GetMapping("/me/profile")
    public Map<String, Object> profile(HttpServletRequest req) {
        var session = req.getSession(false);
        var user = policy.currentUser(session);
        if (user == null) throw new ApiException(401, "Not signed in");
        Map<String, Object> out = new java.util.HashMap<>();
        out.put("user", user);
        if (user.staffId() != null) {
            jdbc.queryForList("""
                SELECT s.full_name, s.staff_type, d.department_id, d.name AS department
                FROM staff s LEFT JOIN departments d ON d.department_id = s.department_id
                WHERE s.staff_id = ?
                """, user.staffId()).stream().findFirst().ifPresent(r -> out.put("staff", r));
        }
        if (user.patientId() != null) {
            jdbc.queryForList(
                "SELECT full_name, mrn, dob, sex FROM patients WHERE patient_id = ?",
                user.patientId()).stream().findFirst().ifPresent(r -> out.put("patient", r));
        }
        return out;
    }
}
