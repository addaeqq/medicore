package com.medicore.lab;

import java.util.List;
import java.util.Map;

/**
 * The laboratory order lifecycle (FR-LAB-04) as a pure function of the current
 * status. Framework-free and database-free, like PolicyEngine / SlotGenerator /
 * FefoAllocator: the service wraps it with persistence.
 *
 * ordered -> sample_collected -> in_progress -> result_entered -> released
 *
 * Only a doctor may take the final step (FR-LAB-05 / AC-04): results stay
 * invisible to the patient until they are released.
 */
public final class LabWorkflow {
    private LabWorkflow() {}

    public static final String ORDERED          = "ordered";
    public static final String SAMPLE_COLLECTED = "sample_collected";
    public static final String IN_PROGRESS      = "in_progress";
    public static final String RESULT_ENTERED   = "result_entered";
    public static final String RELEASED         = "released";

    /** The single legal successor of each status; null once the order is released. */
    private static final Map<String, String> NEXT = Map.of(
        ORDERED,          SAMPLE_COLLECTED,
        SAMPLE_COLLECTED, IN_PROGRESS,
        IN_PROGRESS,      RESULT_ENTERED,
        RESULT_ENTERED,   RELEASED
    );

    /** Statuses a lab technician may move an order into; RELEASED is the doctor's (FR-LAB-05). */
    public static final List<String> LAB_TECH_STATUSES = List.of(SAMPLE_COLLECTED, IN_PROGRESS, RESULT_ENTERED);

    public static String next(String current) {
        return NEXT.get(current);
    }

    /** Forward-only: a status may advance one step, never skip and never go back. */
    public static boolean canAdvance(String from, String to) {
        return to != null && to.equals(NEXT.get(from));
    }

    public static boolean isLabTechStep(String to) {
        return LAB_TECH_STATUSES.contains(to);
    }

    /** Results may only be entered while the sample is actually being worked on. */
    public static boolean acceptsResults(String status) {
        return IN_PROGRESS.equals(status) || RESULT_ENTERED.equals(status);
    }

    /**
     * An order is ready for the doctor only once every requested test has a result;
     * a half-finished panel must not reach the patient.
     */
    public static boolean readyForRelease(String status, int itemCount, int itemsWithResults) {
        return RESULT_ENTERED.equals(status) && itemCount > 0 && itemsWithResults == itemCount;
    }

    /** Human-readable reason a transition was refused, for the 422 body. */
    public static String rejection(String from, String to) {
        if (RELEASED.equals(from)) return "This order is already released and cannot change";
        String expected = NEXT.get(from);
        if (expected == null) return "Unknown status '" + from + "'";
        return "A '" + from + "' order can only move to '" + expected + "', not '" + to + "'";
    }
}
