package com.medicore.lab;

import com.medicore.audit.AuditService;
import com.medicore.common.ApiException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Laboratory domain service (FR-LAB-01..05). LabWorkflow owns the lifecycle rules;
 * this class is the persistence shell around it (Design §2.2).
 */
@Service
public class LabService {
    private final JdbcTemplate jdbc;
    private final AuditService audit;

    public LabService(JdbcTemplate jdbc, AuditService audit) {
        this.jdbc = jdbc; this.audit = audit;
    }

    /** Patient context is needed before authorisation for the doctor-side release check. */
    public UUID patientOfOrder(UUID labOrderId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT patient_id FROM lab_orders WHERE lab_order_id = ?", labOrderId);
        if (rows.isEmpty()) throw new ApiException(404, "Lab order not found");
        return (UUID) rows.get(0).get("patient_id");
    }

    public List<Map<String, Object>> catalogue() {
        return jdbc.queryForList(
            "SELECT lab_test_id, name, specimen, price, tat_hours FROM lab_tests ORDER BY name");
    }

    public UUID patientOfConsultation(UUID consultationId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT patient_id FROM consultations WHERE consultation_id = ?", consultationId);
        if (rows.isEmpty()) throw new ApiException(404, "Consultation not found");
        return (UUID) rows.get(0).get("patient_id");
    }

    /** FR-LAB-01: a doctor requests tests against the consultation that justifies them. */
    @Transactional
    public UUID order(UUID consultationId, List<UUID> testIds, UUID doctorStaffId, UUID userId) {
        if (testIds == null || testIds.isEmpty()) throw new ApiException(422, "Choose at least one test");
        UUID patientId = patientOfConsultation(consultationId);
        UUID orderId = UUID.randomUUID();
        jdbc.update("""
            INSERT INTO lab_orders (lab_order_id, consultation_id, patient_id, ordered_by, status)
            VALUES (?,?,?,?, 'ordered')
            """, orderId, consultationId, patientId, doctorStaffId);
        for (UUID testId : testIds.stream().distinct().toList()) {
            jdbc.update("INSERT INTO lab_order_items (order_item_id, lab_order_id, lab_test_id) VALUES (?,?,?)",
                UUID.randomUUID(), orderId, testId);
        }
        audit.log(userId, patientId, "lab.order", "lab_orders:" + orderId,
            "{\"tests\":" + testIds.size() + "}");
        return orderId;
    }

    private Map<String, Object> orderRow(UUID labOrderId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT lab_order_id, patient_id, status FROM lab_orders WHERE lab_order_id = ?", labOrderId);
        if (rows.isEmpty()) throw new ApiException(404, "Lab order not found");
        return rows.get(0);
    }

    /** FR-LAB-04: the bench worklist — everything still in flight, newest request last. */
    public List<Map<String, Object>> worklist() {
        return jdbc.queryForList("""
            SELECT o.lab_order_id, o.status, o.created_at,
                   p.patient_id, p.full_name AS patient, p.mrn,
                   s.full_name AS ordered_by,
                   count(i.order_item_id) AS test_count,
                   count(i.result_value)  AS results_in
            FROM lab_orders o
            JOIN patients p ON p.patient_id = o.patient_id
            JOIN staff s    ON s.staff_id   = o.ordered_by
            LEFT JOIN lab_order_items i ON i.lab_order_id = o.lab_order_id
            WHERE o.status <> 'released'
            GROUP BY o.lab_order_id, o.status, o.created_at, p.patient_id, p.full_name, p.mrn, s.full_name
            ORDER BY o.created_at
            """);
    }

    /** Recently released orders, so the bench can see what has left the laboratory. */
    public List<Map<String, Object>> recentlyReleased() {
        return jdbc.queryForList("""
            SELECT o.lab_order_id, o.status, o.created_at,
                   p.full_name AS patient, p.mrn,
                   count(i.order_item_id) AS test_count,
                   max(i.released_at) AS released_at
            FROM lab_orders o
            JOIN patients p ON p.patient_id = o.patient_id
            LEFT JOIN lab_order_items i ON i.lab_order_id = o.lab_order_id
            WHERE o.status = 'released'
            GROUP BY o.lab_order_id, o.status, o.created_at, p.full_name, p.mrn
            ORDER BY max(i.released_at) DESC NULLS LAST
            LIMIT 10
            """);
    }

    public Map<String, Object> orderDetail(UUID labOrderId) {
        Map<String, Object> o = orderRow(labOrderId);
        List<Map<String, Object>> items = jdbc.queryForList("""
            SELECT i.order_item_id, i.result_value, i.ref_range, i.released_at,
                   t.name, t.specimen, t.price, t.tat_hours,
                   e.full_name AS entered_by
            FROM lab_order_items i
            JOIN lab_tests t ON t.lab_test_id = i.lab_test_id
            LEFT JOIN staff e ON e.staff_id = i.entered_by
            WHERE i.lab_order_id = ?
            ORDER BY t.name
            """, labOrderId);
        Map<String, Object> patient = jdbc.queryForList(
            "SELECT full_name, mrn, dob, sex FROM patients WHERE patient_id = ?", o.get("patient_id"))
            .stream().findFirst().orElseThrow(() -> new ApiException(404, "Patient not found"));
        String status = (String) o.get("status");
        return Map.of(
            "labOrderId", labOrderId,
            "patientId", o.get("patient_id"),
            "patient", patient,
            "status", status,
            "items", items,
            "nextStatus", LabWorkflow.next(status) == null ? "" : LabWorkflow.next(status));
    }

    /** FR-LAB-04: forward-only, one step at a time. */
    @Transactional
    public String advance(UUID labOrderId, String to, UUID userId) {
        Map<String, Object> o = orderRow(labOrderId);
        String from = (String) o.get("status");
        if (!LabWorkflow.canAdvance(from, to)) throw new ApiException(422, LabWorkflow.rejection(from, to));
        if (!LabWorkflow.isLabTechStep(to))
            throw new ApiException(403, "Releasing results to the patient is the ordering doctor's step (FR-LAB-05)");
        if (LabWorkflow.RESULT_ENTERED.equals(to)) {
            Counts c = counts(labOrderId);
            if (c.items == 0 || c.withResults < c.items)
                throw new ApiException(422, "Enter a result for every requested test first ("
                    + c.withResults + " of " + c.items + " done)");
        }
        jdbc.update("UPDATE lab_orders SET status = ? WHERE lab_order_id = ?", to, labOrderId);
        audit.log(userId, (UUID) o.get("patient_id"), "lab.process", "lab_orders:" + labOrderId,
            "{\"from\":\"" + from + "\",\"to\":\"" + to + "\"}");
        return to;
    }

    /** FR-LAB-03: a result belongs to one requested test and records who entered it. */
    @Transactional
    public void recordResult(UUID orderItemId, String resultValue, String refRange, UUID staffId, UUID userId) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT i.order_item_id, o.lab_order_id, o.status, o.patient_id
            FROM lab_order_items i JOIN lab_orders o ON o.lab_order_id = i.lab_order_id
            WHERE i.order_item_id = ?
            """, orderItemId);
        if (rows.isEmpty()) throw new ApiException(404, "Requested test not found");
        Map<String, Object> row = rows.get(0);
        String status = (String) row.get("status");
        if (!LabWorkflow.acceptsResults(status))
            throw new ApiException(422, "Results can only be entered once the sample is in progress (this order is '"
                + status + "')");
        jdbc.update("UPDATE lab_order_items SET result_value = ?, ref_range = ?, entered_by = ? WHERE order_item_id = ?",
            resultValue, refRange, staffId, orderItemId);
        audit.log(userId, (UUID) row.get("patient_id"), "lab.process",
            "lab_order_items:" + orderItemId, "{\"action\":\"result_entered\"}");
    }

    /**
     * FR-LAB-05 / AC-04: the ordering clinician releases; only then does released_at
     * get stamped, which is what makes the result visible to the patient.
     */
    @Transactional
    public void release(UUID labOrderId, UUID userId) {
        Map<String, Object> o = orderRow(labOrderId);
        String from = (String) o.get("status");
        Counts c = counts(labOrderId);
        if (!LabWorkflow.readyForRelease(from, c.items, c.withResults))
            throw new ApiException(422, LabWorkflow.RESULT_ENTERED.equals(from)
                ? "Every test needs a result before release (" + c.withResults + " of " + c.items + ")"
                : "Only an order with all results entered can be released (this one is '" + from + "')");
        jdbc.update("UPDATE lab_order_items SET released_at = now() WHERE lab_order_id = ? AND released_at IS NULL",
            labOrderId);
        jdbc.update("UPDATE lab_orders SET status = 'released' WHERE lab_order_id = ?", labOrderId);
        audit.log(userId, (UUID) o.get("patient_id"), "lab.release", "lab_orders:" + labOrderId,
            "{\"tests\":" + c.items + "}");
    }

    /**
     * A patient's laboratory history. releasedOnly is the AC-04 gate: a patient sees
     * a pending order exists, but never an unreleased value.
     */
    public List<Map<String, Object>> patientOrders(UUID patientId, boolean releasedOnly) {
        List<Map<String, Object>> orders = jdbc.queryForList("""
            SELECT o.lab_order_id, o.status, o.created_at, s.full_name AS ordered_by
            FROM lab_orders o JOIN staff s ON s.staff_id = o.ordered_by
            WHERE o.patient_id = ? ORDER BY o.created_at DESC LIMIT 50
            """, patientId);
        for (Map<String, Object> o : orders) {
            List<Map<String, Object>> items = jdbc.queryForList("""
                SELECT t.name, t.specimen,
                       CASE WHEN ? AND i.released_at IS NULL THEN NULL ELSE i.result_value END AS result_value,
                       i.ref_range, i.released_at
                FROM lab_order_items i JOIN lab_tests t ON t.lab_test_id = i.lab_test_id
                WHERE i.lab_order_id = ? ORDER BY t.name
                """, releasedOnly, o.get("lab_order_id"));
            o.put("items", items);
        }
        return orders;
    }

    private record Counts(int items, int withResults) {}

    private Counts counts(UUID labOrderId) {
        Map<String, Object> r = jdbc.queryForMap("""
            SELECT count(*) AS items, count(result_value) AS with_results
            FROM lab_order_items WHERE lab_order_id = ?
            """, labOrderId);
        return new Counts(((Number) r.get("items")).intValue(), ((Number) r.get("with_results")).intValue());
    }
}
