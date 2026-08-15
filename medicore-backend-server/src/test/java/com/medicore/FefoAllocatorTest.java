package com.medicore;

import com.medicore.pharmacy.FefoAllocator;
import com.medicore.pharmacy.FefoAllocator.Allocation;
import com.medicore.pharmacy.FefoAllocator.BatchView;
import com.medicore.pharmacy.FefoAllocator.InsufficientStock;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Pure unit tests for FEFO allocation (FR-PHM-02, Design Fig. 7). */
class FefoAllocatorTest {
    private final LocalDate today = LocalDate.of(2026, 8, 14);
    private final UUID b1 = new UUID(0, 1);
    private final UUID b2 = new UUID(0, 2);

    @Test
    void drawsFromEarliestExpiryFirst() {
        var earliest = new BatchView(b1, today.plusDays(10), 5);
        var later = new BatchView(b2, today.plusDays(60), 8);
        var out = FefoAllocator.allocate(List.of(later, earliest), 3, today);
        assertEquals(List.of(new Allocation(b1, 3)), out);
    }

    @Test
    void spansMultipleBatchesInExpiryOrder() {
        var earliest = new BatchView(b1, today.plusDays(10), 5);
        var later = new BatchView(b2, today.plusDays(60), 8);
        var out = FefoAllocator.allocate(List.of(later, earliest), 9, today);
        assertEquals(List.of(new Allocation(b1, 5), new Allocation(b2, 4)), out);
    }

    @Test
    void neverAllocatesExpiredStock() {
        var expired = new BatchView(b1, today.minusDays(1), 100);
        var usable = new BatchView(b2, today.plusDays(30), 8);
        var out = FefoAllocator.allocate(List.of(expired, usable), 8, today);
        assertEquals(List.of(new Allocation(b2, 8)), out);
    }

    @Test
    void insufficientStockThrowsWithShortfallAndAllocatesNothing() {
        var only = new BatchView(b1, today.plusDays(10), 5);
        var ex = assertThrows(InsufficientStock.class, () ->
            FefoAllocator.allocate(List.of(only), 8, today));
        assertEquals(3, ex.shortfall());
    }

    @Test
    void equalExpiryTieBreaksDeterministically() {
        var a = new BatchView(b1, today.plusDays(10), 4);
        var b = new BatchView(b2, today.plusDays(10), 4);
        var out = FefoAllocator.allocate(List.of(b, a), 6, today);
        assertEquals(b1, out.get(0).batchId());
    }

    @Test
    void rejectsNonPositiveRequests() {
        assertThrows(IllegalArgumentException.class, () ->
            FefoAllocator.allocate(List.of(new BatchView(b1, today.plusDays(5), 5)), 0, today));
    }
}
