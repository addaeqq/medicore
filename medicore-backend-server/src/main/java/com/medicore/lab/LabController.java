package com.medicore.lab;

import com.medicore.common.SessionUser;
import com.medicore.policy.PolicyContext;
import com.medicore.policy.PolicyService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Laboratory bench (lab_tech) and result release (ordering doctor). FR-LAB-03..05. */
@RestController
@RequestMapping("/api/lab")
public class LabController {
    private final LabService lab;
    private final PolicyService policy;

    public LabController(LabService lab, PolicyService policy) {
        this.lab = lab; this.policy = policy;
    }

    public record AdvanceRequest(@NotBlank String status) {}
    public record OrderRequest(@NotNull UUID consultationId, @NotEmpty List<UUID> testIds) {}

    /** The test catalogue a doctor picks from, and the bench uses for reference. */
    @GetMapping("/tests")
    public Map<String, Object> tests(HttpServletRequest req) {
        policy.authorize(req.getSession(false), "lab.catalogue", PolicyContext.none());
        return Map.of("tests", lab.catalogue());
    }

    // FR-LAB-01: ordering is scoped to a doctor with an active care relationship.
    @PostMapping("/orders")
    public ResponseEntity<Map<String, Object>> order(@Valid @RequestBody OrderRequest r, HttpServletRequest req) {
        SessionUser user = policy.authorize(req.getSession(false), "lab.order",
            PolicyContext.patient(lab.patientOfConsultation(r.consultationId())));
        UUID id = lab.order(r.consultationId(), r.testIds(), user.staffId(), user.userId());
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("labOrderId", id));
    }
    public record ResultRequest(@NotBlank @Size(max = 255) String resultValue,
                                @Size(max = 120) String refRange) {}

    /** FR-LAB-04: everything on the bench, plus what has recently left it. */
    @GetMapping("/orders")
    public Map<String, Object> worklist(HttpServletRequest req) {
        policy.authorize(req.getSession(false), "lab.process", PolicyContext.none());
        return Map.of("orders", lab.worklist(), "released", lab.recentlyReleased());
    }

    @GetMapping("/orders/{id}")
    public Map<String, Object> detail(@PathVariable UUID id, HttpServletRequest req) {
        policy.authorize(req.getSession(false), "lab.process", PolicyContext.none());
        return lab.orderDetail(id);
    }

    /** Sample collected -> in progress -> results entered. Release is the doctor's step. */
    @PostMapping("/orders/{id}/advance")
    public Map<String, Object> advance(@PathVariable UUID id, @Valid @RequestBody AdvanceRequest r,
                                       HttpServletRequest req) {
        SessionUser user = policy.authorize(req.getSession(false), "lab.process", PolicyContext.none());
        return Map.of("status", lab.advance(id, r.status(), user.userId()));
    }

    // FR-LAB-03
    @PostMapping("/items/{itemId}/result")
    public Map<String, Object> result(@PathVariable UUID itemId, @Valid @RequestBody ResultRequest r,
                                      HttpServletRequest req) {
        SessionUser user = policy.authorize(req.getSession(false), "lab.process", PolicyContext.none());
        lab.recordResult(itemId, r.resultValue(), r.refRange(), user.staffId(), user.userId());
        return Map.of("recorded", true);
    }

    /**
     * FR-LAB-05 / AC-04: releasing is what makes a result visible to the patient, so it
     * is scoped to a doctor with an active care relationship — never the laboratory.
     */
    @PostMapping("/orders/{id}/release")
    public Map<String, Object> release(@PathVariable UUID id, HttpServletRequest req) {
        SessionUser user = policy.authorize(req.getSession(false), "lab.release",
            PolicyContext.patient(lab.patientOfOrder(id)));
        lab.release(id, user.userId());
        return Map.of("released", true);
    }

    /** A patient's laboratory history; patients themselves see released values only. */
    @GetMapping("/patients/{patientId}/orders")
    public Map<String, Object> byPatient(@PathVariable UUID patientId, HttpServletRequest req) {
        SessionUser user = policy.authorize(req.getSession(false), "lab.read_released",
            PolicyContext.patient(patientId));
        boolean releasedOnly = "patient".equals(user.role()) || "family".equals(user.role());
        return Map.of("orders", lab.patientOrders(patientId, releasedOnly));
    }
}
