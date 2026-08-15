package com.medicore.policy;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** ReBAC resolvers backed by the live schema (FR-AUTH-05, AC-03, FR-FAM-01/02). */
@Component
public class JdbcRelationshipResolver implements RelationshipResolver {
    private final JdbcTemplate jdbc;
    public JdbcRelationshipResolver(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    private boolean exists(String sql, Object... args) {
        Boolean b = jdbc.queryForObject("SELECT EXISTS(" + sql + ")", Boolean.class, args);
        return Boolean.TRUE.equals(b);
    }

    @Override
    public boolean patientOwns(UUID userId, UUID patientId) {
        return exists("SELECT 1 FROM patients WHERE patient_id = ? AND user_id = ?", patientId, userId);
    }

    @Override
    public boolean doctorHasActiveRelationship(UUID doctorStaffId, UUID patientId) {
        // Active appointment with this doctor, or active admission under their care (referrals: Phase 2).
        return exists("""
            SELECT 1 FROM appointments a JOIN slots s ON s.slot_id = a.slot_id
            WHERE a.patient_id = ? AND s.doctor_id = ?
              AND a.status IN ('booked','checked_in','in_consultation')
            """, patientId, doctorStaffId)
            || exists("SELECT 1 FROM admissions WHERE patient_id = ? AND admitting_doctor = ? AND status = 'active'",
                patientId, doctorStaffId);
    }

    @Override
    public boolean nurseWardMatches(UUID nurseStaffId, UUID patientId) { // AC-03
        return exists("""
            SELECT 1 FROM admissions a
            JOIN beds b ON b.bed_id = a.bed_id
            JOIN rooms r ON r.room_id = b.room_id
            JOIN staff st ON st.assigned_ward_id = r.ward_id
            WHERE a.patient_id = ? AND a.status = 'active' AND st.staff_id = ?
            """, patientId, nurseStaffId);
    }

    @Override
    public boolean grantCovers(UUID granteeUserId, UUID patientId, String scopeNeeded) { // FR-FAM-01/02
        return exists("""
            SELECT 1 FROM access_grants
            WHERE grantee_user_id = ? AND patient_id = ? AND revoked_at IS NULL
              AND expires_at > now() AND ? = ANY(scope)
            """, granteeUserId, patientId, scopeNeeded);
    }
}
