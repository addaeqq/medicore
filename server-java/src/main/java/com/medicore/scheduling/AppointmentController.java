package com.medicore.scheduling;

import com.medicore.common.ApiException;
import com.medicore.common.SessionUser;
import com.medicore.domain.Appointment;
import com.medicore.policy.PolicyContext;
import com.medicore.policy.PolicyService;
import com.medicore.repo.Repositories.AppointmentRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/appointments")
public class AppointmentController {
    private final SchedulingService scheduling;
    private final PolicyService policy;
    private final AppointmentRepository appointments;

    public AppointmentController(SchedulingService scheduling, PolicyService policy,
                                 AppointmentRepository appointments) {
        this.scheduling = scheduling; this.policy = policy; this.appointments = appointments;
    }

    public record BookRequest(@NotNull UUID slotId, UUID patientId) {}

    // FR-APT-03: browse slots
    @GetMapping("/slots")
    public Map<String, Object> slots(@RequestParam(required = false) UUID doctorId,
                                     @RequestParam(required = false) UUID departmentId,
                                     HttpServletRequest req) {
        policy.authorize(req.getSession(false), "slot.list", PolicyContext.none());
        return Map.of("slots", scheduling.availableSlots(doctorId, departmentId));
    }

    // FR-APT-03/04: patients book for themselves; reception may book for any patient
    @PostMapping
    public ResponseEntity<Map<String, Object>> book(@Valid @RequestBody BookRequest r, HttpServletRequest req) {
        var session = req.getSession(false);
        SessionUser pre = policy.currentUser(session);
        UUID targetPatient = (pre != null && "patient".equals(pre.role())) ? pre.patientId() : r.patientId();
        SessionUser user = policy.authorize(session, "appointment.book", PolicyContext.patient(targetPatient));
        if (targetPatient == null) throw new ApiException(422, "patientId is required for staff bookings");
        Appointment appt = scheduling.bookAppointment(r.slotId(), targetPatient, user.userId());
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
            "appointmentId", appt.getAppointmentId(), "slotId", appt.getSlotId(), "status", appt.getStatus()));
    }

    // FR-APT-05
    @PostMapping("/{id}/cancel")
    public Map<String, Object> cancel(@PathVariable UUID id, HttpServletRequest req) {
        var session = req.getSession(false);
        SessionUser user = policy.authorize(session, "appointment.cancel", PolicyContext.none());
        Appointment appt = appointments.findById(id).orElseThrow(() -> new ApiException(404, "Appointment not found"));
        if ("patient".equals(user.role()) && !appt.getPatientId().equals(user.patientId()))
            throw new ApiException(403, "Not permitted"); // ownership re-check against the row
        scheduling.cancelAppointment(id);
        return Map.of("cancelled", true);
    }

    // FR-APT-07
    @PostMapping("/{id}/checkin")
    public ResponseEntity<Map<String, Object>> checkin(@PathVariable UUID id, HttpServletRequest req) {
        policy.authorize(req.getSession(false), "queue.checkin", PolicyContext.none());
        var entry = scheduling.checkIn(id);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("queueEntryId", entry.getQueueEntryId()));
    }

    // FR-APT-08
    @GetMapping("/queue/{departmentId}")
    public Map<String, Object> queue(@PathVariable UUID departmentId, HttpServletRequest req) {
        policy.authorize(req.getSession(false), "queue.view", PolicyContext.none());
        return Map.of("queue", scheduling.departmentQueue(departmentId));
    }
}
