package com.medicore.pharmacy;

import com.medicore.audit.AuditService;
import com.medicore.common.ApiException;
import com.medicore.common.SessionUser;
import com.medicore.domain.*;
import com.medicore.repo.Repositories.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Pharmacy domain (FR-PHM-01..06): prescriptions, FEFO dispensing (Design Fig. 7),
 * stock intake and low-stock reporting. Concurrency: batch rows are locked with
 * SELECT ... FOR UPDATE inside the dispense transaction; the schema CHECK
 * (qty_on_hand >= 0) is the final backstop.
 */
@Service
public class PharmacyService {

    private final DrugRepository drugs;
    private final StockBatchRepository batches;
    private final PrescriptionRepository prescriptions;
    private final PrescriptionItemRepository items;
    private final DispenseRepository dispenses;
    private final ConsultationRepository consultations;
    private final JdbcTemplate jdbc;
    private final AuditService audit;
    private final jakarta.persistence.EntityManager em;

    public PharmacyService(DrugRepository drugs, StockBatchRepository batches,
                           PrescriptionRepository prescriptions, PrescriptionItemRepository items,
                           DispenseRepository dispenses, ConsultationRepository consultations,
                           JdbcTemplate jdbc, AuditService audit,
                           jakarta.persistence.EntityManager em) {
        this.drugs = drugs; this.batches = batches; this.prescriptions = prescriptions;
        this.items = items; this.dispenses = dispenses; this.consultations = consultations;
        this.jdbc = jdbc; this.audit = audit; this.em = em;
    }

    /** Patient of a consultation, for pre-authorisation context in the controller. */
    public UUID prescriptionDetailPatient(UUID consultationId) {
        return consultations.findById(consultationId)
            .orElseThrow(() -> new ApiException(404, "Consultation not found")).getPatientId();
    }

    // ---------- Prescribing (FR-PHM-01) ----------

    public record RxItemRequest(UUID drugId, String dose, String frequency, Short durationDays, int quantity) {}

    @Transactional
    public Prescription createPrescription(UUID consultationId, List<RxItemRequest> itemRequests, SessionUser doctor) {
        Consultation c = consultations.findById(consultationId)
            .orElseThrow(() -> new ApiException(404, "Consultation not found"));
        if (!c.getDoctorId().equals(doctor.staffId()))
            throw new ApiException(403, "Only the consulting doctor may prescribe on this encounter");
        if (itemRequests == null || itemRequests.isEmpty())
            throw new ApiException(422, "A prescription needs at least one item");

        Prescription rx = new Prescription(consultationId, doctor.staffId(), c.getPatientId());
        prescriptions.save(rx);
        for (RxItemRequest r : itemRequests) {
            drugs.findById(r.drugId()).orElseThrow(() -> new ApiException(422, "Unknown drug: " + r.drugId()));
            if (r.quantity() <= 0) throw new ApiException(422, "Quantity must be positive");
            items.save(new PrescriptionItem(rx.getPrescriptionId(), r.drugId(), r.dose(),
                r.frequency(), r.durationDays(), r.quantity()));
        }
        audit.log(doctor.userId(), c.getPatientId(), "rx.write",
            "prescriptions:" + rx.getPrescriptionId(), null);
        return rx;
    }

    /** Pharmacist worklist: open / partially dispensed prescriptions with remaining quantities. */
    public List<Map<String, Object>> openPrescriptions() {
        return jdbc.queryForList("""
            SELECT p.prescription_id, p.status, p.created_at, pt.full_name AS patient, pt.mrn,
                   count(i.rx_item_id) AS item_count
            FROM prescriptions p
            JOIN patients pt ON pt.patient_id = p.patient_id
            JOIN prescription_items i ON i.prescription_id = p.prescription_id
            WHERE p.status IN ('open','partially_dispensed')
            GROUP BY p.prescription_id, p.status, p.created_at, pt.full_name, pt.mrn
            ORDER BY p.created_at
            """);
    }

    /** Detail view for dispensing: items with remaining qty, plus the patient's allergy list (safety). */
    public Map<String, Object> prescriptionDetail(UUID prescriptionId) {
        Prescription rx = prescriptions.findById(prescriptionId)
            .orElseThrow(() -> new ApiException(404, "Prescription not found"));
        List<Map<String, Object>> itemRows = jdbc.queryForList("""
            SELECT i.rx_item_id, i.dose, i.frequency, i.duration_days, i.quantity,
                   d.generic_name, d.strength, d.form,
                   i.quantity - COALESCE((SELECT sum(qty) FROM dispenses ds WHERE ds.rx_item_id = i.rx_item_id), 0)
                     AS remaining
            FROM prescription_items i JOIN drugs d ON d.drug_id = i.drug_id
            WHERE i.prescription_id = ? ORDER BY d.generic_name
            """, prescriptionId);
        List<Map<String, Object>> allergyRows = jdbc.queryForList(
            "SELECT substance, severity FROM allergies WHERE patient_id = ?", rx.getPatientId());
        return Map.of("prescriptionId", prescriptionId, "status", rx.getStatus(),
            "items", itemRows, "allergies", allergyRows);
    }

    // ---------- Dispensing (FR-PHM-02/03/05, Design Fig. 7) ----------

    public record DispenseRequest(UUID rxItemId, int qty) {}

    @Transactional
    public Map<String, Object> dispense(UUID prescriptionId, List<DispenseRequest> requests, SessionUser pharmacist) {
        Prescription rx = prescriptions.findById(prescriptionId)
            .orElseThrow(() -> new ApiException(404, "Prescription not found"));
        if (!"open".equals(rx.getStatus()) && !"partially_dispensed".equals(rx.getStatus()))
            throw new ApiException(422, "Prescription is not dispensable (status: " + rx.getStatus() + ")");
        if (requests == null || requests.isEmpty())
            throw new ApiException(422, "Nothing to dispense");

        List<PrescriptionItem> rxItems = items.findByPrescriptionId(prescriptionId);
        LocalDate today = LocalDate.now();
        int totalDispensedNow = 0;

        for (DispenseRequest req : requests) {
            PrescriptionItem item = rxItems.stream()
                .filter(i -> i.getRxItemId().equals(req.rxItemId())).findFirst()
                .orElseThrow(() -> new ApiException(422, "Item does not belong to this prescription"));
            if (req.qty() <= 0) throw new ApiException(422, "Dispense quantity must be positive");

            Integer already = jdbc.queryForObject(
                "SELECT COALESCE(sum(qty),0) FROM dispenses WHERE rx_item_id = ?", Integer.class, item.getRxItemId());
            int remaining = item.getQuantity() - (already == null ? 0 : already);
            if (req.qty() > remaining)
                throw new ApiException(422, "Requested " + req.qty() + " exceeds remaining " + remaining);

            // Lock usable batches for this drug (FEFO order) for the duration of the transaction.
            List<FefoAllocator.BatchView> usable = jdbc.query("""
                SELECT batch_id, expiry_date, qty_on_hand FROM stock_batches
                WHERE drug_id = ? AND qty_on_hand > 0 AND expiry_date >= ?
                ORDER BY expiry_date, batch_id
                FOR UPDATE
                """,
                (rs, n) -> new FefoAllocator.BatchView(
                    UUID.fromString(rs.getString("batch_id")),
                    rs.getObject("expiry_date", LocalDate.class),
                    rs.getInt("qty_on_hand")),
                item.getDrugId(), today);

            List<FefoAllocator.Allocation> allocations;
            try {
                allocations = FefoAllocator.allocate(usable, req.qty(), today);
            } catch (FefoAllocator.InsufficientStock e) {
                throw new ApiException(409, "Insufficient usable stock (short by " + e.shortfall() + ")");
            }

            for (FefoAllocator.Allocation a : allocations) {
                int updated = jdbc.update(
                    "UPDATE stock_batches SET qty_on_hand = qty_on_hand - ? WHERE batch_id = ? AND qty_on_hand >= ?",
                    a.qty(), a.batchId(), a.qty());
                if (updated != 1) throw new ApiException(409, "Stock changed concurrently - retry"); // FR-PHM-05
                dispenses.save(new Dispense(item.getRxItemId(), a.batchId(), a.qty(), pharmacist.staffId()));
            }
            totalDispensedNow += req.qty();
        }

        // Recompute prescription status from the durable record (FR-PHM-03).
        // The sums below read the tables directly, so the dispense rows written above
        // must be flushed first or this run's quantities would not be counted.
        em.flush();
        Integer prescribed = jdbc.queryForObject(
            "SELECT COALESCE(sum(quantity),0) FROM prescription_items WHERE prescription_id = ?",
            Integer.class, prescriptionId);
        Integer dispensedTotal = jdbc.queryForObject("""
            SELECT COALESCE(sum(d.qty),0) FROM dispenses d
            JOIN prescription_items i ON i.rx_item_id = d.rx_item_id
            WHERE i.prescription_id = ?
            """, Integer.class, prescriptionId);
        String newStatus = (dispensedTotal != null && prescribed != null && dispensedTotal >= prescribed)
            ? "dispensed" : "partially_dispensed";
        rx.setStatus(newStatus);
        prescriptions.save(rx);

        audit.log(pharmacist.userId(), rx.getPatientId(), "rx.dispense",
            "prescriptions:" + prescriptionId, "{\"qty\":" + totalDispensedNow + "}");
        return Map.of("prescriptionId", prescriptionId, "status", newStatus, "dispensed", totalDispensedNow);
    }

    // ---------- Inventory (FR-PHM-04/06) ----------

    /**
     * FR-PHM-04. The duplicate check is here for the message; uq_drugs_identity (V901) is the
     * invariant — a near-duplicate splits stock across two drug_ids and lets FEFO and billing
     * key off whichever row the prescriber picked.
     */
    @Transactional
    public Drug createDrug(String genericName, String brandName, String form, String strength,
                           BigDecimal unitPrice, Integer reorderLevel) {
        Long existing = jdbc.queryForObject("""
            SELECT count(*) FROM drugs
            WHERE lower(btrim(generic_name)) = lower(btrim(?))
              AND lower(btrim(strength))     = lower(btrim(?))
              AND lower(btrim(form))         = lower(btrim(?))
              AND lower(btrim(coalesce(brand_name, ''))) = lower(btrim(coalesce(?, '')))
            """, Long.class, genericName, strength, form, brandName);
        if (existing != null && existing > 0)
            throw new ApiException(409, "That drug is already in the formulary");
        return drugs.save(new Drug(genericName, brandName, form, strength, unitPrice,
            reorderLevel == null ? 10 : reorderLevel));
    }

    @Transactional
    public StockBatch receiveBatch(UUID drugId, String batchNo, LocalDate expiryDate, int qty, BigDecimal unitCost) {
        drugs.findById(drugId).orElseThrow(() -> new ApiException(404, "Drug not found"));
        if (qty <= 0) throw new ApiException(422, "Received quantity must be positive");
        if (expiryDate.isBefore(LocalDate.now())) throw new ApiException(422, "Cannot receive expired stock");
        return batches.save(new StockBatch(drugId, batchNo, expiryDate, qty, unitCost));
    }

    public List<Map<String, Object>> listDrugsWithStock() {
        return jdbc.queryForList("""
            SELECT d.drug_id, d.generic_name, d.brand_name, d.form, d.strength, d.unit_price, d.reorder_level,
                   COALESCE(sum(b.qty_on_hand), 0) AS total_on_hand
            FROM drugs d LEFT JOIN stock_batches b
              ON b.drug_id = d.drug_id AND b.expiry_date >= CURRENT_DATE
            GROUP BY d.drug_id ORDER BY d.generic_name
            """);
    }

    /** FR-PHM-06: drugs at or below reorder level (usable stock only). */
    public List<Map<String, Object>> lowStock() {
        return jdbc.queryForList("""
            SELECT d.drug_id, d.generic_name, d.strength, d.reorder_level,
                   COALESCE(sum(b.qty_on_hand), 0) AS total_on_hand
            FROM drugs d LEFT JOIN stock_batches b
              ON b.drug_id = d.drug_id AND b.expiry_date >= CURRENT_DATE
            GROUP BY d.drug_id
            HAVING COALESCE(sum(b.qty_on_hand), 0) <= d.reorder_level
            ORDER BY total_on_hand
            """);
    }
}
