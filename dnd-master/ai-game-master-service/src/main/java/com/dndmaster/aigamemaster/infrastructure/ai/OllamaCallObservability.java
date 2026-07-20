package com.dndmaster.aigamemaster.infrastructure.ai;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Circuit state and payload-free call telemetry for one Ollama model kind. */
public final class OllamaCallObservability {
    private final String modelKind;
    private final int failureThreshold;
    private final Duration resetTimeout;
    private final Clock clock;
    private final Consumer<String> eventSink;
    private int consecutiveFailures;
    private long successes;
    private long failures;
    private long timeouts;
    private long circuitRejections;
    private Instant openedAt;

    public OllamaCallObservability(int failureThreshold, Duration resetTimeout) {
        this("ollama", failureThreshold, resetTimeout, Clock.systemUTC(), ignored -> { });
    }

    public OllamaCallObservability(
            String modelKind, int failureThreshold, Duration resetTimeout, Consumer<String> eventSink) {
        this(modelKind, failureThreshold, resetTimeout, Clock.systemUTC(), eventSink);
    }

    OllamaCallObservability(
            String modelKind, int failureThreshold, Duration resetTimeout, Clock clock, Consumer<String> eventSink) {
        if (failureThreshold < 1 || resetTimeout == null || resetTimeout.isNegative() || resetTimeout.isZero()) {
            throw new IllegalArgumentException("Circuit configuration must be positive");
        }
        this.modelKind = requireSafeKind(modelKind);
        this.failureThreshold = failureThreshold;
        this.resetTimeout = resetTimeout;
        this.clock = Objects.requireNonNull(clock);
        this.eventSink = Objects.requireNonNull(eventSink);
    }

    public <T> T invoke(Supplier<T> invocation) {
        beforeCall();
        try {
            T result = invocation.get();
            recordSuccess();
            return result;
        } catch (RuntimeException exception) {
            recordFailure(exception);
            throw exception;
        }
    }

    public synchronized void beforeCall() {
        if (isOpen()) {
            circuitRejections++;
            eventSink.accept("ai.provider=" + modelKind + " status=circuit_rejected payload=[REDACTED]");
            throw new OllamaCircuitOpenException();
        }
    }

    public synchronized void recordSuccess() {
        consecutiveFailures = 0;
        openedAt = null;
        successes++;
        eventSink.accept("ai.provider=" + modelKind + " status=success payload=[REDACTED]");
    }

    public synchronized void recordFailure(RuntimeException exception) {
        Objects.requireNonNull(exception);
        failures++;
        consecutiveFailures++;
        if (isTimeout(exception)) {
            timeouts++;
        }
        if (consecutiveFailures >= failureThreshold) {
            openedAt = clock.instant();
        }
        eventSink.accept("ai.provider=" + modelKind + " status=failure error="
                + exception.getClass().getSimpleName() + " payload=[REDACTED]");
    }

    public synchronized CircuitState state() {
        return isOpen() ? CircuitState.OPEN : CircuitState.CLOSED;
    }

    public synchronized Snapshot snapshot() {
        return new Snapshot(successes, failures, timeouts, circuitRejections, state());
    }

    private boolean isOpen() {
        if (openedAt != null && !clock.instant().isBefore(openedAt.plus(resetTimeout))) {
            consecutiveFailures = 0;
            openedAt = null;
        }
        return openedAt != null;
    }

    private static boolean isTimeout(Throwable error) {
        for (Throwable current = error; current != null; current = current.getCause()) {
            if (current instanceof ProviderTimeoutException || current instanceof java.util.concurrent.TimeoutException
                    || current instanceof java.net.SocketTimeoutException) {
                return true;
            }
        }
        return false;
    }

    private static String requireSafeKind(String value) {
        if (value == null || !value.matches("[a-z][a-z0-9_-]*")) {
            throw new IllegalArgumentException("Model kind must be a safe identifier");
        }
        return value;
    }

    public record Snapshot(long successes, long failures, long timeouts, long circuitRejections, CircuitState state) { }

    public enum CircuitState { CLOSED, OPEN }
}
