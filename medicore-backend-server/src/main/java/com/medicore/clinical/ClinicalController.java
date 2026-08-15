package com.medicore.clinical;

import com.medicore.common.ApiException;
import com.medicore.common.SessionUser;
import com.medicore.policy.PolicyContext;
import com.medicore.policy.PolicyService;
import com.medicore.repo.Repositories.AppointmentRepository;
import com.medicore.repo.Repositories.ConsultationRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class ClinicalController {
    private final ConsultationService clinical;
    private final ConsultationRepository consultations;
    private final AppointmentRepository appointments;
    private final PolicyService policy;

    public ClinicalController(ConsultationService clinical, ConsultationRepository consultations,
                              AppointmentRepository appointments, PolicyService policy) {
        this.clinical = clinical; this.consultations = consultations;
        this.appointments = appointments; this.policy = policy;
    }

    public record StartRequest(@NotNull UUID appointmentId) {}
    public record NotesRequest(String complaint, String findings, String diagnosis) {}
    public record AddendumRequest(@NotBlank String body) {}
    public record AllergyRequest(@NotBlank String substance,
                                 @NotBlank @Pattern(regexp = "mild|moderate|severe") String severity) {}

    /** Patient context is resolved BEFORE authorisation so ReBAC checks run against the right record. */
    private UUID patientOfAppointment(UUID appointmentId) {
        return appointments.findById(appointmentId)
            .orElseThrow(() -> new ApiException(404, "Appointment not found")).getPatientId();
    }

    private UUID patientOfConsultation(UUID consultationId) {
        return consultations.findById(consultationId)
            .orElseThrow(() -> new ApiException(404, "Consultation not found")).getPatientId();
    }

    // FR-EMR-01
    @PostMapping("/consultations/start")
    public ResponseEntity<Map<String, Object>> start(@Valid @RequestBody StartRequest r, HttpServletRequest req) {
        SessionUser doctor = policy.authorize(req.getSession(false), "emr.write",
            PolicyContext.patient(patientOfAppointment(r.appointmentId())));
        var c = clinical.start(r.appointmentId(), doctor);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("consultationId", c.getConsultationId()));
    }

    // FR-EMR-02
    @PatchMapping("/consultations/{id}")
    public Map<String, Object> update(@PathVariable UUID id, @RequestBody NotesRequest r, HttpServletRequest req) {
        SessionUser doctor = policy.authorize(req.getSession(false), "emr.write",
            PolicyContext.patient(patientOfConsultation(id)));
        var c = clinical.updateNotes(id, doctor, r.complaint(), r.findings(), r.diagnosis());
        return Map.of("consultationId", c.getConsultationId(), "signed", c.getSignedAt() != null);
    }

    // FR-EMR-03
    @PostMapping("/consultations/{id}/sign")
    public Map<String, Object> sign(@PathVariable UUID id, HttpServletRequest req) {
        SessionUser doctor = policy.authorize(req.getSession(false), "emr.write",
            PolicyContext.patient(patientOfConsultation(id)));
        var c = clinical.sign(id, doctor);
        return Map.of("consultationId", c.getConsultationId(), "signedAt", c.getSignedAt());
    }

    // FR-EMR-04: post-completion the active relationship may have ended, so the action is
    // emr.addendum (doctor: ANY) and the service enforces author == consultation.doctor.
    @PostMapping("/consultations/{id}/addendums")
    public ResponseEntity<Map<String, Object>> addendum(@PathVariable UUID id,
                                                        @Valid @RequestBody AddendumRequest r,
                                                        HttpServletRequest req) {
        SessionUser doctor = policy.authorize(req.getSession(false), "emr.addendum",
            PolicyContext.patient(patientOfConsultation(id)));
        var a = clinical.addAddendum(id, doctor, r.body());
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("addendumId", a.getAddendumId()));
    }

    // Consultation detail for the clinical workspace (emr.read scope applies)
    @GetMapping("/consultations/{id}")
    public Map<String, Object> consultation(@PathVariable UUID id, HttpServletRequest req) {
        policy.authorize(req.getSession(false), "emr.read", PolicyContext.patient(patientOfConsultation(id)));
        return clinical.consultationDetail(id);
    }

    // FR-EMR-01/06
    @GetMapping("/patients/{patientId}/emr")
    public Map<String, Object> emr(@PathVariable UUID patientId, HttpServletRequest req) {
        policy.authorize(req.getSession(false), "emr.read", PolicyContext.patient(patientId));
        return clinical.patientEmr(patientId);
    }

    // FR-EMR-05 (subset)
    @PostMapping("/patients/{patientId}/allergies")
    public ResponseEntity<Map<String, Object>> addAllergy(@PathVariable UUID patientId,
                                                          @Valid @RequestBody AllergyRequest r,
                                                          HttpServletRequest req) {
        policy.authorize(req.getSession(false), "allergy.write", PolicyContext.patient(patientId));
        var al = clinical.addAllergy(patientId, r.substance(), r.severity());
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("allergyId", al.getAllergyId()));
    }

    @GetMapping("/patients/{patientId}/allergies")
    public Map<String, Object> allergies(@PathVariable UUID patientId, HttpServletRequest req) {
        policy.authorize(req.getSession(false), "allergy.read", PolicyContext.patient(patientId));
        return Map.of("allergies", clinical.listAllergies(patientId));
    }
}
