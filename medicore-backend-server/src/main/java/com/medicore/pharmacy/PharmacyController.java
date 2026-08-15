package com.medicore.pharmacy;

import com.medicore.common.SessionUser;
import com.medicore.policy.PolicyContext;
import com.medicore.policy.PolicyService;
import com.medicore.repo.Repositories.PrescriptionRepository;
import com.medicore.common.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class PharmacyController {
    private final PharmacyService pharmacy;
    private final PrescriptionRepository prescriptions;
    private final PolicyService policy;

    public PharmacyController(PharmacyService pharmacy, PrescriptionRepository prescriptions,
                              PolicyService policy) {
        this.pharmacy = pharmacy; this.prescriptions = prescriptions; this.policy = policy;
    }

    public record RxItemBody(@NotNull UUID drugId, @NotBlank String dose, @NotBlank String frequency,
                             Short durationDays, @Min(1) int quantity) {}
    public record CreateRxRequest(@NotNull UUID consultationId, @NotEmpty @Valid List<RxItemBody> items) {}
    public record DispenseBody(@NotNull UUID rxItemId, @Min(1) int qty) {}
    public record DispenseRequestBody(@NotEmpty @Valid List<DispenseBody> items) {}
    public record CreateDrugRequest(@NotBlank String genericName, String brandName, @NotBlank String form,
                                    @NotBlank String strength, @NotNull @DecimalMin("0.0") BigDecimal unitPrice,
                                    @Min(0) Integer reorderLevel) {}
    public record ReceiveBatchRequest(@NotNull UUID drugId, @NotBlank String batchNo,
                                      @NotNull LocalDate expiryDate, @Min(1) int qty,
                                      @DecimalMin("0.0") BigDecimal unitCost) {}

    private UUID patientOfRx(UUID prescriptionId) {
        return prescriptions.findById(prescriptionId)
            .orElseThrow(() -> new ApiException(404, "Prescription not found")).getPatientId();
    }

    // FR-PHM-01
    @PostMapping("/prescriptions")
    public ResponseEntity<Map<String, Object>> create(@Valid @RequestBody CreateRxRequest r, HttpServletRequest req) {
        // patient ctx from the consultation is resolved inside the service; authorize against it first
        SessionUser doctor = policy.authorize(req.getSession(false), "rx.write",
            PolicyContext.patient(pharmacy.prescriptionDetailPatient(r.consultationId())));
        var items = r.items().stream().map(i -> new PharmacyService.RxItemRequest(
            i.drugId(), i.dose(), i.frequency(), i.durationDays(), i.quantity())).toList();
        var rx = pharmacy.createPrescription(r.consultationId(), items, doctor);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
            "prescriptionId", rx.getPrescriptionId(), "status", rx.getStatus()));
    }

    // Pharmacist worklist (rx.read: pharmacist ANY)
    @GetMapping("/prescriptions/open")
    public Map<String, Object> open(HttpServletRequest req) {
        policy.authorize(req.getSession(false), "rx.dispense", PolicyContext.none());
        return Map.of("prescriptions", pharmacy.openPrescriptions());
    }

    @GetMapping("/prescriptions/{id}")
    public Map<String, Object> detail(@PathVariable UUID id, HttpServletRequest req) {
        policy.authorize(req.getSession(false), "rx.read", PolicyContext.patient(patientOfRx(id)));
        return pharmacy.prescriptionDetail(id);
    }

    // FR-PHM-02/03: FEFO dispense
    @PostMapping("/prescriptions/{id}/dispense")
    public Map<String, Object> dispense(@PathVariable UUID id, @Valid @RequestBody DispenseRequestBody r,
                                        HttpServletRequest req) {
        SessionUser pharmacist = policy.authorize(req.getSession(false), "rx.dispense", PolicyContext.none());
        var items = r.items().stream().map(i -> new PharmacyService.DispenseRequest(i.rxItemId(), i.qty())).toList();
        return pharmacy.dispense(id, items, pharmacist);
    }

    // FR-PHM-04
    @PostMapping("/inventory/drugs")
    public ResponseEntity<Map<String, Object>> createDrug(@Valid @RequestBody CreateDrugRequest r,
                                                          HttpServletRequest req) {
        policy.authorize(req.getSession(false), "inventory.manage", PolicyContext.none());
        var d = pharmacy.createDrug(r.genericName(), r.brandName(), r.form(), r.strength(),
            r.unitPrice(), r.reorderLevel());
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("drugId", d.getDrugId()));
    }

    @PostMapping("/inventory/batches")
    public ResponseEntity<Map<String, Object>> receive(@Valid @RequestBody ReceiveBatchRequest r,
                                                       HttpServletRequest req) {
        policy.authorize(req.getSession(false), "inventory.manage", PolicyContext.none());
        var b = pharmacy.receiveBatch(r.drugId(), r.batchNo(), r.expiryDate(), r.qty(), r.unitCost());
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("batchId", b.getBatchId()));
    }

    @GetMapping("/inventory/drugs")
    public Map<String, Object> drugs(HttpServletRequest req) {
        policy.authorize(req.getSession(false), "inventory.read", PolicyContext.none());
        return Map.of("drugs", pharmacy.listDrugsWithStock());
    }

    // FR-PHM-06
    @GetMapping("/inventory/low-stock")
    public Map<String, Object> lowStock(HttpServletRequest req) {
        policy.authorize(req.getSession(false), "inventory.read", PolicyContext.none());
        return Map.of("lowStock", pharmacy.lowStock());
    }
}
