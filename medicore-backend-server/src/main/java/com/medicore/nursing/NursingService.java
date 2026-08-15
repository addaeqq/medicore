package com.medicore.nursing;

import com.medicore.audit.AuditService;
import com.medicore.common.ApiException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Ward board and observations (FR-FAC-01, FR-EMR-05). */
@Service
public class NursingService {
    private final JdbcTemplate jdbc;
    private final AuditService audit;

    public NursingService(JdbcTemplate jdbc, AuditService audit) {
        this.jdbc = jdbc; this.audit = audit;
    }

    /** The ward a nurse is posted to (AC-03). Null for staff who are not ward-based. */
    public UUID assignedWard(UUID staffId) {
        if (staffId == null) return null;
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT assigned_ward_id FROM staff WHERE staff_id = ?", staffId);
        return rows.isEmpty() ? null : (UUID) rows.get(0).get("assigned_ward_id");
    }

    public List<Map<String, Object>> wards() {
        return jdbc.queryForList("SELECT ward_id, name, daily_tariff FROM wards ORDER BY name");
    }

    public Map<String, Object> ward(UUID wardId) {
        return jdbc.queryForList("SELECT ward_id, name, daily_tariff FROM wards WHERE ward_id = ?", wardId)
            .stream().findFirst().orElseThrow(() -> new ApiException(404, "Ward not found"));
    }

    /**
     * Every bed on the ward with its occupant, if any. A bed with no active admission
     * comes back with null patient fields — that is the empty-bed case, not an error.
     */
    public List<Map<String, Object>> board(UUID wardId) {
        return jdbc.queryForList("""
            SELECT b.bed_id, b.label, b.status AS bed_status, r.room_no,
                   a.admission_id, a.admitted_at,
                   p.patient_id, p.full_name AS patient, p.mrn, p.dob, p.sex,
                   s.full_name AS admitting_doctor,
                   EXTRACT(DAY FROM (now() - a.admitted_at))::int + 1 AS day_number,
                   (SELECT count(*) FROM allergies al WHERE al.patient_id = p.patient_id) AS allergy_count,
                   (SELECT max(v.recorded_at) FROM vitals v WHERE v.patient_id = p.patient_id) AS last_observation
            FROM beds b
            JOIN rooms r ON r.room_id = b.room_id
            LEFT JOIN admissions a ON a.bed_id = b.bed_id AND a.status = 'active'
            LEFT JOIN patients p ON p.patient_id = a.patient_id
            LEFT JOIN staff s ON s.staff_id = a.admitting_doctor
            WHERE r.ward_id = ?
            ORDER BY r.room_no, b.label
            """, wardId);
    }

    /** FR-EMR-05: the observation chart, most recent first. */
    public List<Map<String, Object>> vitals(UUID patientId) {
        return jdbc.queryForList("""
            SELECT v.vitals_id, v.bp_sys, v.bp_dia, v.temp_c, v.pulse, v.spo2, v.weight_kg,
                   v.recorded_at, s.full_name AS recorded_by
            FROM vitals v JOIN staff s ON s.staff_id = v.recorded_by
            WHERE v.patient_id = ? ORDER BY v.recorded_at DESC LIMIT 30
            """, patientId);
    }

    @Transactional
    public UUID recordVitals(UUID patientId, UUID staffId, UUID userId,
                             Integer bpSys, Integer bpDia, BigDecimal tempC,
                             Integer pulse, Integer spo2, BigDecimal weightKg) {
        if (bpSys == null && bpDia == null && tempC == null && pulse == null && spo2 == null && weightKg == null)
            throw new ApiException(422, "Record at least one observation");
        if (staffId == null) throw new ApiException(422, "Only clinical staff can record observations");
        UUID id = UUID.randomUUID();
        jdbc.update("""
            INSERT INTO vitals (vitals_id, patient_id, recorded_by, bp_sys, bp_dia, temp_c, pulse, spo2, weight_kg)
            VALUES (?,?,?,?,?,?,?,?,?)
            """, id, patientId, staffId, bpSys, bpDia, tempC, pulse, spo2, weightKg);
        audit.log(userId, patientId, "vitals.write", "vitals:" + id, "{\"source\":\"ward_board\"}");
        return id;
    }
}
