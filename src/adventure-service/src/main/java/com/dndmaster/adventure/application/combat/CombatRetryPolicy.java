package com.dndmaster.adventure.application.combat;

import java.time.Duration;

/** Combat external work is attempted once immediately and twice with bounded backoff. */
public final class CombatRetryPolicy {
    public static final int MAX_ATTEMPTS = 3;

    private CombatRetryPolicy() {}

    public static Duration backoffAfterAttempt(int attempt) {
        if (attempt < 1 || attempt > MAX_ATTEMPTS) throw new IllegalArgumentException("attempt must be between 1 and 3");
        return switch (attempt) {
            case 1 -> Duration.ZERO;
            case 2 -> Duration.ofSeconds(1);
            default -> Duration.ofSeconds(2);
        };
    }

    public static boolean shouldRetry(Throwable failure, int attempt) {
        if (failure == null || attempt < 1 || attempt >= MAX_ATTEMPTS) return false;
        return !(failure instanceof CombatCommandRejectedException);
    }
}
