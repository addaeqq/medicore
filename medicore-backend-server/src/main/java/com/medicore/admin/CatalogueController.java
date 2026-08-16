package com.medicore.admin;

import com.medicore.common.SessionUser;
import com.medicore.policy.PolicyContext;
import com.medicore.policy.PolicyService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Reference catalogue administration — sys_admin only, via the admin.catalogues action.
 * Reads reuse the existing directory endpoints (GET /api/departments, GET /api/lab/tests),
 * both of which already admit sys_admin.
 */
@RestController
@RequestMapping("/api/admin")
public class CatalogueController {

    private final CatalogueService catalogues;
    private final PolicyService policy;

    public CatalogueController(CatalogueService catalogues, PolicyService policy) {
        this.catalogues = catalogues; this.policy = policy;
    }

    public record CreateLabTestRequest(
        @NotBlank @Size(max = 160) String name,
        @NotBlank @Size(max = 60) String specimen,
        @NotNull @DecimalMin("0.0") BigDecimal price,
        @Min(1) @Max(720) Short tatHours) {}

    public record CreateDepartmentRequest(
        @NotBlank @Size(max = 120) String name,
        @NotBlank @Pattern(regexp = "clinical|diagnostic|support") String deptType,
        @NotNull @DecimalMin("0.0") BigDecimal consultFee) {}

    @PostMapping("/lab-tests")
    public ResponseEntity<Map<String, Object>> createLabTest(@Valid @RequestBody CreateLabTestRequest r,
                                                             HttpServletRequest req) {
        SessionUser actor = policy.authorize(req.getSession(false), "admin.catalogues", PolicyContext.none());
        var id = catalogues.createLabTest(r.name(), r.specimen(), r.price(), r.tatHours(), actor.userId());
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("labTestId", id));
    }

    @PostMapping("/departments")
    public ResponseEntity<Map<String, Object>> createDepartment(@Valid @RequestBody CreateDepartmentRequest r,
                                                                HttpServletRequest req) {
        SessionUser actor = policy.authorize(req.getSession(false), "admin.catalogues", PolicyContext.none());
        var id = catalogues.createDepartment(r.name(), r.deptType(), r.consultFee(), actor.userId());
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("departmentId", id));
    }
}
