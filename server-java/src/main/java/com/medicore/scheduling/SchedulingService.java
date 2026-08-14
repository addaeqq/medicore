package com.medicore.scheduling;

import com.medicore.audit.AuditService;
import com.medicore.common.ApiException;
import com.medicore.domain.*;
import com.medicore.repo.Repositories.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Scheduling & Queue domain service (FR-APT-01..08). Transaction boundaries live here (Design §2.2). */
@Service
public class SchedulingService {

    private final ScheduleRepository schedules;
    private final SlotRepository slots;
    private final AppointmentRepository appointments;
    private final QueueEntryRepository queueEntries;
    private final StaffRepository staff;
    private final JdbcTemplate jdbc;
    private final AuditService audit;
    private final int cancelCutoffHours;

    public SchedulingService(ScheduleRepository schedules, SlotRepository slots,
                             AppointmentRepository appointments, QueueEntryRepository queueEntries,
                             StaffRepository staff, JdbcTemplate jdbc, AuditService audit,
                             @Value("${medicore.cancel-cutoff-hours:2}") int cancelCutoffHours) {
        this.schedules = schedules; this.slots = slots; this.appointments = appointments;
        this.queueEntries = queueEntries; this.staff = staff; this.jdbc = jdbc; this.audit = audit;
        this.cancelCutoffHours = cancelCutoffHours;
    }

    public record ScheduleResult(UUID scheduleId, int slotsCreated) {}

    /** FR-APT-01/02: create schedule and materialise slots (DD-04; synchronous generation is TD-01). */
    @Transactional
    public ScheduleResult createScheduleWithSlots(UUID doctorId, int weekday, java.time.LocalTime start,
                                                  java.time.LocalTime end, int slotMinutes, String room,
                                                  int horizonDays) {
        staff.findById(doctorId).orElseThrow(() -> new ApiException(404, "Doctor not found"));
        Schedule schedule = new Schedule(doctorId, (short) weekday, start, end, (short) slotMinutes, room);
        schedules.save(schedule);

        List<SlotGenerator.SlotTime> times = SlotGenerator.generate(
            weekday, start, end, slotMinutes, LocalDate.now(ZoneOffset.UTC), horizonDays);

        // Batch insert with ON CONFLICT DO NOTHING: overlapping schedules can't double-generate (FR-APT-02).
        List<Object[]> rows = times.stream().map(t -> new Object[]{
            UUID.randomUUID(), schedule.getScheduleId(), doctorId,
            Timestamp.from(t.startsAt()), Timestamp.from(t.endsAt())
        }).toList();
        jdbc.batchUpdate(
            "INSERT INTO slots (slot_id, schedule_id, doctor_id, starts_at, ends_at) VALUES (?,?,?,?,?) " +
            "ON CONFLICT (doctor_id, starts_at) DO NOTHING", rows);
        return new ScheduleResult(schedule.getScheduleId(), times.size());
    }

    /** FR-APT-03/04: book a slot. Under a race the UNIQUE(slot_id) insert resolves the winner (Fig. 6). */
    @Transactional
    public Appointment bookAppointment(UUID slotId, UUID patientId, UUID bookedByUserId) {
        Slot slot = slots.findById(slotId).orElseThrow(() -> new ApiException(404, "Slot not found"));
        if (!"available".equals(slot.getStatus())) throw new ApiException(409, "Slot no longer available");
        if (slot.getStartsAt().isBefore(Instant.now())) throw new ApiException(422, "Slot is in the past");

        Staff doctor = staff.findById(slot.getDoctorId()).orElseThrow(() -> new ApiException(500, "Doctor missing"));
        if (doctor.getDepartmentId() == null) throw new ApiException(422, "Doctor has no department");

        try {
            Appointment appt = new Appointment(slotId, patientId, doctor.getDepartmentId());
            appointments.saveAndFlush(appt); // flush now so a unique violation is catchable here
            slot.setStatus("booked");
            slots.save(slot);
            audit.log(bookedByUserId, patientId, "appointment.book",
                "appointments:" + appt.getAppointmentId(), null);
            return appt;
        } catch (DataIntegrityViolationException e) {
            throw new ApiException(409, "Slot was just taken - please pick another"); // FR-APT-04
        }
    }

    /** FR-APT-05: cancel a booked appointment before the cutoff; frees the slot. */
    @Transactional
    public void cancelAppointment(UUID appointmentId) {
        Appointment appt = appointments.findById(appointmentId)
            .orElseThrow(() -> new ApiException(404, "Appointment not found"));
        if (!"booked".equals(appt.getStatus()))
            throw new ApiException(422, "Only booked appointments can be cancelled");
        Slot slot = slots.findById(appt.getSlotId()).orElseThrow(() -> new ApiException(500, "Slot missing"));
        if (Duration.between(Instant.now(), slot.getStartsAt()).toHours() < cancelCutoffHours)
            throw new ApiException(422, "Cancellation cutoff is " + cancelCutoffHours + "h before the slot");
        appt.setStatus("cancelled");
        appointments.save(appt);
        slot.setStatus("available");
        slots.save(slot);
    }

    /** FR-APT-07: reception check-in -> queue entry (priority 100 default; triage lowers it, DD-06). */
    @Transactional
    public QueueEntry checkIn(UUID appointmentId) {
        Appointment appt = appointments.findById(appointmentId)
            .orElseThrow(() -> new ApiException(404, "Appointment not found"));
        if (!"booked".equals(appt.getStatus()))
            throw new ApiException(422, "Cannot check in from status '" + appt.getStatus() + "'");
        appt.setStatus("checked_in");
        appointments.save(appt);
        return queueEntries.save(new QueueEntry(appointmentId));
    }

    /** FR-APT-08: live queue for a department ordered by (priority, checked_in_at). */
    public List<Map<String, Object>> departmentQueue(UUID departmentId) {
        return jdbc.queryForList("""
            SELECT q.queue_entry_id, q.status, q.checked_in_at, q.priority, p.full_name, p.mrn
            FROM queue_entries q
            JOIN appointments a ON a.appointment_id = q.appointment_id
            JOIN patients p ON p.patient_id = a.patient_id
            WHERE a.department_id = ? AND q.status IN ('waiting','in_consultation')
            ORDER BY q.priority, q.checked_in_at
            """, departmentId);
    }

    /** FR-APT-03: browse available future slots. */
    public List<Map<String, Object>> availableSlots(UUID doctorId, UUID departmentId) {
        StringBuilder sql = new StringBuilder("""
            SELECT sl.slot_id, sl.starts_at, sl.ends_at, st.full_name AS doctor, d.name AS department, d.consult_fee
            FROM slots sl
            JOIN staff st ON st.staff_id = sl.doctor_id
            LEFT JOIN departments d ON d.department_id = st.department_id
            WHERE sl.status = 'available' AND sl.starts_at > now()
            """);
        java.util.List<Object> args = new java.util.ArrayList<>();
        if (doctorId != null) { sql.append(" AND sl.doctor_id = ?"); args.add(doctorId); }
        if (departmentId != null) { sql.append(" AND st.department_id = ?"); args.add(departmentId); }
        sql.append(" ORDER BY sl.starts_at LIMIT 200");
        return jdbc.queryForList(sql.toString(), args.toArray());
    }
}
