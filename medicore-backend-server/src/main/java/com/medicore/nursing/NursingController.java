package com.medicore.nursing;

import com.medicore.common.ApiException;
import com.medicore.common.SessionUser;
import com.medicore.policy.PolicyContext;
import com.medicore.policy.PolicyService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Ward board and observation charting — the nursing workspace (AC-03, FR-EMR-05). */
@RestController
@RequestMapping("/api")
public class NursingController {
    private final NursingService nursing;
    private final PolicyService policy;

    public NursingController(NursingService nursing, PolicyService policy) {
        this.nursing = nursing; this.policy = policy;
    }

    public record VitalsRequest(@Min(50) @Max(300) Integer bpSys,
                               @Min(20) @Max(200) Integer bpDia,
                               @DecimalMin("25.0") @DecimalMax("45.0") BigDecimal tempC,
                               @Min(20) @Max(250) Integer pulse,
                               @Min(50) @Max(100) Integer spo2,
                               @DecimalMin("0.5") @DecimalMax("400.0") BigDecimal weightKg) {}

    /**
     * The caller's ward board. A nurse is pinned to their own assigned_ward_id and cannot
     * ask for another ward's board; doctors, management and sys-admin may name one.
     */
    @GetMapping("/nursing/ward")
    public Map<String, Object> ward(@RequestParam(required = false) UUID wardId, HttpServletRequest req) {
        SessionUser user = policy.authorize(req.getSession(false), "ward.roster", PolicyContext.none());
        UUID target;
        if ("nurse".equals(user.role())) {
            target = nursing.assignedWard(user.staffId());
            if (target == null) throw new ApiException(422, "You are not assigned to a ward yet");
        } else {
            target = wardId != null ? wardId : nursing.wards().stream().findFirst()
                .map(w -> (UUID) w.get("ward_id"))
                .orElseThrow(() -> new ApiException(404, "No wards configured"));
        }
        Map<String, Object> out = new HashMap<>();
        out.put("ward", nursing.ward(target));
        out.put("beds", nursing.board(target));
        out.put("pinned", "nurse".equals(user.role()));   // the UI hides the ward picker when true
        return out;
    }

    @GetMapping("/nursing/wards")
    public Map<String, Object> wards(HttpServletRequest req) {
        policy.authorize(req.getSession(false), "ward.roster", PolicyContext.none());
        return Map.of("wards", nursing.wards());
    }

    // FR-EMR-05: WARD scope means a nurse only reaches patients admitted to their ward.
    @GetMapping("/patients/{patientId}/vitals")
    public Map<String, Object> vitals(@PathVariable UUID patientId, HttpServletRequest req) {
        policy.authorize(req.getSession(false), "vitals.read", PolicyContext.patient(patientId));
        return Map.of("vitals", nursing.vitals(patientId));
    }

    @PostMapping("/patients/{patientId}/vitals")
    public ResponseEntity<Map<String, Object>> recordVitals(@PathVariable UUID patientId,
                                                            @Valid @RequestBody VitalsRequest r,
                                                            HttpServletRequest req) {
        SessionUser user = policy.authorize(req.getSession(false), "vitals.write", PolicyContext.patient(patientId));
        UUID id = nursing.recordVitals(patientId, user.staffId(), user.userId(),
            r.bpSys(), r.bpDia(), r.tempC(), r.pulse(), r.spo2(), r.weightKg());
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("vitalsId", id));
    }
}
