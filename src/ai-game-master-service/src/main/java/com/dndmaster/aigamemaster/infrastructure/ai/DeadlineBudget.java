package com.dndmaster.aigamemaster.infrastructure.ai;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/** Monotonic wall-clock budget shared by retrieval, generation, repair, and validation. */
public final class DeadlineBudget {
    private final Instant deadline;
    private final Duration totalBudget;
    private final Duration retrievalBudget;

    private DeadlineBudget(Instant deadline, Duration totalBudget, Duration retrievalBudget) {
        this.deadline = deadline;
        this.totalBudget = totalBudget;
        this.retrievalBudget = retrievalBudget;
    }

    public static DeadlineBudget start(Duration totalBudget, Duration retrievalBudget) {
        validate(totalBudget, retrievalBudget);
        return at(totalBudget, retrievalBudget);
    }

    static DeadlineBudget at(Duration totalBudget, Duration retrievalBudget) {
        return at(Instant.now().plus(totalBudget), totalBudget, retrievalBudget);
    }

    static DeadlineBudget at(Instant deadline, Duration totalBudget, Duration retrievalBudget) {
        validate(totalBudget, retrievalBudget);
        return new DeadlineBudget(Objects.requireNonNull(deadline), totalBudget,
                retrievalBudget.compareTo(totalBudget) > 0 ? totalBudget : retrievalBudget);
    }

    public Duration totalBudget() { return totalBudget; }

    public Duration retrievalBudget() { return retrievalBudget; }

    public Duration remaining() {
        return Duration.between(Instant.now(), deadline);
    }

    public Duration child(Duration requested) {
        Objects.requireNonNull(requested, "requested");
        if (requested.isNegative() || requested.isZero()) throw new IllegalArgumentException("requested budget must be positive");
        Duration remaining = remaining();
        if (remaining.isNegative() || remaining.isZero()) throw new ProviderTimeoutException(new java.util.concurrent.TimeoutException("turn deadline exhausted"));
        return requested.compareTo(remaining) < 0 ? requested : remaining;
    }

    public Duration toMillisDuration() { return child(retrievalBudget); }

    public void requireRemaining(Duration minimum) {
        Objects.requireNonNull(minimum, "minimum");
        if (minimum.isNegative() || remaining().compareTo(minimum) < 0) {
            throw new ProviderTimeoutException(new java.util.concurrent.TimeoutException("turn deadline exhausted"));
        }
    }

    private static void validate(Duration total, Duration retrieval) {
        if (total == null || total.isNegative() || total.isZero() || retrieval == null
                || retrieval.isNegative() || retrieval.isZero()) {
            throw new IllegalArgumentException("deadline budgets must be positive");
        }
    }
}
