package com.medicore.scheduling;

import java.time.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Pure slot expansion (FR-APT-01/02): weekly schedule -> concrete UTC slot times over a horizon.
 * No framework or database dependencies (NFR-MNT-02: unit-tested in isolation).
 */
public final class SlotGenerator {
    private SlotGenerator() {}

    public record SlotTime(Instant startsAt, Instant endsAt) {}

    /**
     * @param weekday      0=Sunday .. 6=Saturday (matches schema CHECK)
     * @param startTime    e.g. LocalTime.of(9, 0)
     * @param endTime      exclusive upper bound for slot end
     * @param slotMinutes  slot length
     * @param fromDate     horizon start (inclusive), interpreted in UTC
     * @param days         horizon length in days
     */
    public static List<SlotTime> generate(int weekday, LocalTime startTime, LocalTime endTime,
                                          int slotMinutes, LocalDate fromDate, int days) {
        List<SlotTime> out = new ArrayList<>();
        int dayStartMin = startTime.getHour() * 60 + startTime.getMinute();
        int dayEndMin = endTime.getHour() * 60 + endTime.getMinute();
        for (int d = 0; d < days; d++) {
            LocalDate day = fromDate.plusDays(d);
            // java.time: SUNDAY=7; schema uses 0=Sunday
            int dow = day.getDayOfWeek().getValue() % 7;
            if (dow != weekday) continue;
            for (int m = dayStartMin; m + slotMinutes <= dayEndMin; m += slotMinutes) {
                Instant starts = day.atStartOfDay(ZoneOffset.UTC).plusMinutes(m).toInstant();
                out.add(new SlotTime(starts, starts.plus(Duration.ofMinutes(slotMinutes))));
            }
        }
        return out;
    }
}
