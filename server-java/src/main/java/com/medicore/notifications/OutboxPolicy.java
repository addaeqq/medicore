package com.medicore.notifications;

/** Pure retry policy for the outbox worker: exponential backoff, capped, then terminal failure. */
public final class OutboxPolicy {
    private OutboxPolicy() {}

    public static final int MAX_ATTEMPTS = 8;

    /** Minutes to wait before the next attempt, given how many attempts have already failed. */
    public static long nextDelayMinutes(int attemptsSoFar) {
        if (attemptsSoFar < 1) return 1;
        long delay = 1L << Math.min(attemptsSoFar, 11);   // 2,4,8,... (2^11 exceeds the cap below)
        return Math.min(delay, 24 * 60);                  // never wait more than 24h
    }

    /** After MAX_ATTEMPTS failures the row becomes terminally 'failed' (visible in ops review). */
    public static boolean exhausted(int attemptsSoFar) { return attemptsSoFar >= MAX_ATTEMPTS; }
}
