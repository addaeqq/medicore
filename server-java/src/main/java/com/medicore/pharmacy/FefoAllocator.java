package com.medicore.pharmacy;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Pure FEFO (first-expiry-first-out) allocation (FR-PHM-02, Design Fig. 7).
 * Framework-free: unit-tested without Spring or a database. The service layer is
 * responsible for row-locking the batch rows (SELECT ... FOR UPDATE) before calling this.
 */
public final class FefoAllocator {
    private FefoAllocator() {}

    public record BatchView(UUID batchId, LocalDate expiry, int qtyOnHand) {}
    public record Allocation(UUID batchId, int qty) {}

    public static class InsufficientStock extends RuntimeException {
        private final int shortfall;
        public InsufficientStock(int shortfall) {
            super("Insufficient stock: short by " + shortfall);
            this.shortfall = shortfall;
        }
        public int shortfall() { return shortfall; }
    }

    /**
     * Allocate {@code needed} units across batches, earliest expiry first, skipping
     * expired batches (expiry before {@code today}) and empty batches.
     * @throws InsufficientStock if usable stock cannot cover the request (nothing is allocated).
     */
    public static List<Allocation> allocate(List<BatchView> batches, int needed, LocalDate today) {
        if (needed <= 0) throw new IllegalArgumentException("needed must be positive");
        List<BatchView> usable = batches.stream()
            .filter(b -> b.qtyOnHand() > 0 && !b.expiry().isBefore(today))
            .sorted(Comparator.comparing(BatchView::expiry).thenComparing(b -> b.batchId().toString()))
            .toList();

        List<Allocation> out = new ArrayList<>();
        int remaining = needed;
        for (BatchView b : usable) {
            if (remaining == 0) break;
            int take = Math.min(b.qtyOnHand(), remaining);
            out.add(new Allocation(b.batchId(), take));
            remaining -= take;
        }
        if (remaining > 0) throw new InsufficientStock(remaining);
        return out;
    }
}
