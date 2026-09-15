package com.dndmaster.relay.infrastructure;

import static org.junit.jupiter.api.Assertions.*;
import com.dndmaster.relay.application.RelayFailureType;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class MicrometerRelayMetricsTest {
    @Test void recordsUtf8BytesDurationFailureAndActiveConnections() {
        var registry = new SimpleMeterRegistry();
        var metrics = new MicrometerRelayMetrics(registry, "relay-a");
        metrics.activeConnections(3);
        metrics.finished(9, 6, Duration.ofMillis(12), RelayFailureType.REMOTE_FAILURE);
        assertEquals(3, registry.get("relay.active.connections").gauge().value());
        assertEquals(9, registry.get("relay.execution.bytes").tag("direction", "request").summary().totalAmount());
        assertEquals(6, registry.get("relay.execution.bytes").tag("direction", "response").summary().totalAmount());
        assertEquals(1, registry.get("relay.execution.count").tag("result", "REMOTE_FAILURE").counter().count());
        assertEquals(1, registry.get("relay.execution.duration").tag("result", "REMOTE_FAILURE").timer().count());
    }
}
