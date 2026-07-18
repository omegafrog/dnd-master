package com.dndmaster.aigamemaster.infrastructure.ai;

import java.time.Clock;
import java.time.Instant;
import java.time.Duration;
import java.util.Objects;
import java.util.function.Supplier;

public final class OllamaCallObservability {
    private final int failureThreshold;
    private final Duration resetTimeout;
    private final Clock clock;
    private int failures;
    private Instant openedAt;

    public OllamaCallObservability(int failureThreshold, Duration resetTimeout) {
        this(failureThreshold, resetTimeout, Clock.systemUTC());
    }

    OllamaCallObservability(int failureThreshold, Duration resetTimeout, Clock clock) {
        if (failureThreshold < 1 || resetTimeout == null || resetTimeout.isNegative() || resetTimeout.isZero()) {
            throw new IllegalArgumentException("Circuit configuration must be positive");
        }
        this.failureThreshold = failureThreshold;
        this.resetTimeout = resetTimeout;
        this.clock = Objects.requireNonNull(clock);
    }

    public synchronized <T> T invoke(Supplier<T> invocation) {
        if (isOpen()) {
            throw new IllegalStateException("Ollama circuit is open");
        }
        try {
            T result = invocation.get();
            failures = 0;
            openedAt = null;
            return result;
        } catch (RuntimeException exception) {
            failures++;
            if (failures >= failureThreshold) {
                openedAt = clock.instant();
            }
            throw exception;
        }
    }

    public synchronized CircuitState state() {
        return isOpen() ? CircuitState.OPEN : CircuitState.CLOSED;
    }

    private boolean isOpen() {
        if (openedAt != null && !clock.instant().isBefore(openedAt.plus(resetTimeout))) {
            failures = 0;
            openedAt = null;
        }
        return openedAt != null;
    }

    public enum CircuitState { CLOSED, OPEN }
}
