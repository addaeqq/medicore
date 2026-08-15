package com.medicore.billing;

import java.math.BigDecimal;
import java.util.List;

/**
 * Pure invoice arithmetic (DD-05: append-only items, totals always computed, never stored).
 * Framework-free and unit-tested in isolation.
 */
public final class BillingMath {
    private BillingMath() {}

    public static BigDecimal sum(List<BigDecimal> amounts) {
        BigDecimal total = BigDecimal.ZERO;
        for (BigDecimal a : amounts) total = total.add(a == null ? BigDecimal.ZERO : a);
        return total;
    }

    /**
     * Status of an ISSUED invoice given computed totals (FR-BIL-05).
     * draft/void are lifecycle states set explicitly by the service, never derived here.
     */
    public static String deriveStatus(BigDecimal total, BigDecimal paid) {
        if (total.signum() <= 0) return "paid";                 // zero-total invoice has nothing owing
        if (paid.compareTo(total) >= 0) return "paid";
        if (paid.signum() > 0) return "partially_paid";
        return "issued";
    }

    public static BigDecimal balance(BigDecimal total, BigDecimal paid) {
        BigDecimal b = total.subtract(paid);
        return b.signum() < 0 ? BigDecimal.ZERO : b;
    }
}
