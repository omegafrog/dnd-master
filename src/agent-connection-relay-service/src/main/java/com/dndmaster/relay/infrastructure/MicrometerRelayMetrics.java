package com.dndmaster.relay.infrastructure;

import com.dndmaster.relay.application.*;
import io.micrometer.core.instrument.*;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

public final class MicrometerRelayMetrics implements RelayMetrics {
    private final MeterRegistry registry;
    private final String instanceId;
    private final AtomicInteger active = new AtomicInteger();
    public MicrometerRelayMetrics(MeterRegistry registry, String instanceId) {
        this.registry = registry; this.instanceId = instanceId;
        Gauge.builder("relay.active.connections", active, AtomicInteger::get).tag("instance", instanceId).register(registry);
    }
    @Override public void finished(long requestBytes, long responseBytes, Duration duration, RelayFailureType failureType) {
        DistributionSummary.builder("relay.execution.bytes").tag("instance", instanceId).tag("implementation", "remote-relay").tag("direction", "request").register(registry).record(requestBytes);
        DistributionSummary.builder("relay.execution.bytes").tag("instance", instanceId).tag("implementation", "remote-relay").tag("direction", "response").register(registry).record(responseBytes);
        String result = failureType == null ? "success" : failureType.name();
        Timer.builder("relay.execution.duration").tag("instance", instanceId).tag("implementation", "remote-relay").tag("result", result).register(registry).record(duration);
        Counter.builder("relay.execution.count").tag("instance", instanceId).tag("implementation", "remote-relay").tag("result", result).register(registry).increment();
    }
    @Override public void activeConnections(int count) { active.set(count); }
}
