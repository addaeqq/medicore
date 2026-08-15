package com.medicore.domain;

import jakarta.persistence.*;
import java.time.LocalTime;
import java.util.UUID;

@Entity @Table(name = "schedules")
public class Schedule {
    @Id @Column(name = "schedule_id") private UUID scheduleId = UUID.randomUUID();
    @Column(name = "doctor_id", nullable = false) private UUID doctorId;
    @Column(nullable = false) private short weekday;
    @Column(name = "start_time", nullable = false) private LocalTime startTime;
    @Column(name = "end_time", nullable = false) private LocalTime endTime;
    @Column(name = "slot_minutes", nullable = false) private short slotMinutes = 20;
    private String room;

    protected Schedule() {}
    public Schedule(UUID doctorId, short weekday, LocalTime startTime, LocalTime endTime, short slotMinutes, String room) {
        this.doctorId = doctorId; this.weekday = weekday; this.startTime = startTime;
        this.endTime = endTime; this.slotMinutes = slotMinutes; this.room = room;
    }
    public UUID getScheduleId() { return scheduleId; }
    public UUID getDoctorId() { return doctorId; }
    public short getWeekday() { return weekday; }
    public LocalTime getStartTime() { return startTime; }
    public LocalTime getEndTime() { return endTime; }
    public short getSlotMinutes() { return slotMinutes; }
}
