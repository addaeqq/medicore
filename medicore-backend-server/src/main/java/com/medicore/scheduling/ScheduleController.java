package com.medicore.scheduling;

import com.medicore.policy.PolicyContext;
import com.medicore.policy.PolicyService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalTime;
import java.util.UUID;

@RestController
@RequestMapping("/api/schedules")
public class ScheduleController {
    private final SchedulingService scheduling;
    private final PolicyService policy;

    public ScheduleController(SchedulingService scheduling, PolicyService policy) {
        this.scheduling = scheduling; this.policy = policy;
    }

    public record CreateScheduleRequest(
        @NotNull UUID doctorId,
        @Min(0) @Max(6) int weekday,
        @NotNull LocalTime startTime,
        @NotNull LocalTime endTime,
        @Min(5) @Max(120) Integer slotMinutes,
        String room) {}

    // FR-APT-01/02
    @PostMapping
    public ResponseEntity<SchedulingService.ScheduleResult> create(
            @Valid @RequestBody CreateScheduleRequest r, HttpServletRequest req) {
        policy.authorize(req.getSession(false), "schedule.manage", PolicyContext.none());
        int slotMinutes = r.slotMinutes() == null ? 20 : r.slotMinutes();
        var out = scheduling.createScheduleWithSlots(
            r.doctorId(), r.weekday(), r.startTime(), r.endTime(), slotMinutes, r.room(), 28);
        return ResponseEntity.status(HttpStatus.CREATED).body(out);
    }
}
