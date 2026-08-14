package com.medicore.clinical;

import com.medicore.audit.AuditService;
import com.medicore.common.ApiException;
import com.medicore.common.SessionUser;
import com.medicore.domain.*;
import com.medicore.repo.Repositories.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * EMR core (FR-EMR-01..06): consultation lifecycle with sign-and-lock,
 * post-signature addendums, allergies, and the patient EMR read model.
 */
@Service
public class ConsultationService {

    private final ConsultationRepository consultations;
    private final AddendumRepository addendums;
    private final AllergyRepository allergies;
    private final AppointmentRepository appointments;
    private final SlotRepository slots;
    private final JdbcTemplate jdbc;
    private final AuditService audit;

    public ConsultationService(ConsultationRepository consultations, AddendumRepository addendums,
                               AllergyRepository allergies, AppointmentRepository appointments,
                               SlotRepository slots, JdbcTemplate jdbc, AuditService audit) {
        this.consultations = consultations; this.addendums = addendums; this.allergies = allergies;
        this.appointments = appointments; this.slots = slots; this.jdbc = jdbc; this.audit = audit;
    }

    /** FR-EMR-01: doctor pulls a checked-in appointment into consultation (queue -> in_consultation). */
    @Transactional
    public Consultation start(UUID appointmentId, SessionUser doctor) {
        Appointment appt = appointments.findById(appointmentId)
            .orElseThrow(() -> new ApiException(404, "Appointment not found"));
        if (!"checked_in".equals(appt.getStatus()))
            throw new ApiException(422, "Patient is not checked in");
        Slot slot = slots.findById(appt.getSlotId()).orElseThrow(() -> new ApiException(500, "Slot missing"));
        if (!slot.getDoctorId().equals(doctor.staffId()))
            throw new ApiException(403, "This appointment belongs to another doctor's clinic");

        appt.setStatus("in_consultation");
        appointments.save(appt);
        jdbc.update("UPDATE queue_entries SET status = 'in_consultation' WHERE appointment_id = ?", appointmentId);

        Consultation c = new Consultation(appointmentId, doctor.staffId(), appt.getPatientId());
        consultations.save(c);
        audit.log(doctor.userId(), appt.getPatientId(), "consultation.start",
            "consultations:" + c.getConsultationId(), null);
        return c;
    }

    /** FR-EMR-02: author edits notes while unsigned. The V4 trigger is the DB backstop. */
    @Transactional
    public Consultation updateNotes(UUID consultationId, SessionUser doctor,
                                    String complaint, String findings, String diagnosis) {
        Consultation c = requireAuthored(consultationId, doctor);
        if (c.getSignedAt() != null)
            throw new ApiException(422, "Consultation is signed; add an addendum instead"); // FR-EMR-03
        if (complaint != null) c.setComplaint(complaint);
        if (findings != null) c.setFindings(findings);
        if (diagnosis != null) c.setDiagnosis(diagnosis);
        return consultations.save(c);
    }

    /** FR-EMR-03: sign-and-lock; completes the appointment and closes the queue entry. */
    @Transactional
    public Consultation sign(UUID consultationId, SessionUser doctor) {
        Consultation c = requireAuthored(consultationId, doctor);
        if (c.getSignedAt() != null) throw new ApiException(422, "Already signed");
        if (c.getDiagnosis() == null || c.getDiagnosis().isBlank())
            throw new ApiException(422, "A diagnosis is required before signing");
        c.setSignedAt(Instant.now());
        consultations.save(c);

        if (c.getAppointmentId() != null) {
            appointments.findById(c.getAppointmentId()).ifPresent(a -> {
                a.setStatus("completed");
                appointments.save(a);
            });
            jdbc.update("UPDATE queue_entries SET status = 'done' WHERE appointment_id = ?", c.getAppointmentId());
        }
        audit.log(doctor.userId(), c.getPatientId(), "consultation.sign",
            "consultations:" + consultationId, null);
        return c;
    }

    /** FR-EMR-04: corrections after signing are append-only addendums by the original author. */
    @Transactional
    public Addendum addAddendum(UUID consultationId, SessionUser doctor, String body) {
        Consultation c = requireAuthored(consultationId, doctor);
        if (c.getSignedAt() == null)
            throw new ApiException(422, "Unsigned consultations are edited directly, not via addendum");
        return addendums.save(new Addendum(consultationId, doctor.staffId(), body));
    }

    /** FR-EMR-05 (subset): allergy list maintenance; unique per (patient, substance). */
    @Transactional
    public Allergy addAllergy(UUID patientId, String substance, String severity) {
        try {
            return allergies.saveAndFlush(new Allergy(patientId, substance.trim(), severity));
        } catch (DataIntegrityViolationException e) {
            throw new ApiException(409, "Allergy already recorded for this patient");
        }
    }

    public List<Allergy> listAllergies(UUID patientId) {
        return allergies.findByPatientId(patientId);
    }

    /** FR-EMR-01/06: the patient EMR read model (consultations + addendums + allergies + prescriptions). */
    public Map<String, Object> patientEmr(UUID patientId) {
        List<Map<String, Object>> cons = jdbc.queryForList("""
            SELECT c.consultation_id, c.complaint, c.findings, c.diagnosis, c.signed_at, c.created_at,
                   s.full_name AS doctor
            FROM consultations c JOIN staff s ON s.staff_id = c.doctor_id
            WHERE c.patient_id = ? ORDER BY c.created_at DESC
            """, patientId);
        List<Map<String, Object>> adds = jdbc.queryForList("""
            SELECT a.addendum_id, a.consultation_id, a.body, a.created_at, s.full_name AS author
            FROM addendums a JOIN staff s ON s.staff_id = a.author_id
            WHERE a.consultation_id IN (SELECT consultation_id FROM consultations WHERE patient_id = ?)
            ORDER BY a.created_at
            """, patientId);
        List<Map<String, Object>> allergyRows = jdbc.queryForList(
            "SELECT substance, severity FROM allergies WHERE patient_id = ? ORDER BY substance", patientId);
        List<Map<String, Object>> rx = jdbc.queryForList("""
            SELECT p.prescription_id, p.status, p.created_at,
                   d.generic_name, i.dose, i.frequency, i.quantity
            FROM prescriptions p
            JOIN prescription_items i ON i.prescription_id = p.prescription_id
            JOIN drugs d ON d.drug_id = i.drug_id
            WHERE p.patient_id = ? ORDER BY p.created_at DESC
            """, patientId);
        return Map.of("consultations", cons, "addendums", adds, "allergies", allergyRows, "prescriptions", rx);
    }

    private Consultation requireAuthored(UUID consultationId, SessionUser doctor) {
        Consultation c = consultations.findById(consultationId)
            .orElseThrow(() -> new ApiException(404, "Consultation not found"));
        if (!c.getDoctorId().equals(doctor.staffId()))
            throw new ApiException(403, "Only the authoring doctor may modify this consultation");
        return c;
    }
}
