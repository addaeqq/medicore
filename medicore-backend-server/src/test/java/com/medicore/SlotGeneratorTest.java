package com.medicore;

import com.medicore.scheduling.SlotGenerator;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.*;

/** Pure unit tests for slot expansion (FR-APT-02, NFR-MNT-02). */
class SlotGeneratorTest {

    @Test
    void generatesCorrectNumberOfSlotsFor3hWindowAt20min() {
        var slots = SlotGenerator.generate(1, LocalTime.of(9, 0), LocalTime.of(12, 0), 20,
            LocalDate.of(2026, 8, 10), 7); // Mon 10 Aug 2026
        assertEquals(9, slots.size());
        assertEquals(Instant.parse("2026-08-10T09:00:00Z"), slots.get(0).startsAt());
        assertEquals(Instant.parse("2026-08-10T12:00:00Z"), slots.get(8).endsAt());
    }

    @Test
    void neverEmitsSlotThatOverrunsEndTime() {
        var slots = SlotGenerator.generate(2, LocalTime.of(9, 0), LocalTime.of(10, 30), 25,
            LocalDate.of(2026, 8, 11), 7);
        assertEquals(3, slots.size()); // 09:00, 09:25, 09:50 — 10:15+25 would overrun
        for (var s : slots) assertFalse(s.endsAt().isAfter(Instant.parse("2026-08-11T10:30:00Z")));
    }

    @Test
    void producesSlotsOnlyOnScheduleWeekdayAcrossHorizon() {
        var slots = SlotGenerator.generate(5, LocalTime.of(8, 0), LocalTime.of(9, 0), 30,
            LocalDate.of(2026, 8, 10), 28); // 4 Fridays x 2
        assertEquals(8, slots.size());
    }
}
