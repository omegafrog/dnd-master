package com.dndmaster.adventure;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.dndmaster.adventure.application.runtime.GmQualityGateReport;
import com.dndmaster.adventure.infrastructure.metrics.MicrometerGmQualityMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

class MicrometerGmQualityMetricsTest {
    @Test
    void exposes_current_pending_saga_backlog_and_summary_samples() {
        var registry = new SimpleMeterRegistry();
        var metrics = new MicrometerGmQualityMetrics(registry);

        metrics.recordSagaPending();
        metrics.recordSagaPending();
        metrics.recordSagaCompleted();
        metrics.record(new GmQualityGateReport(100, 99, 95, 95, 0, 0, 0, 4.0));

        assertEquals(1.0, registry.get("gm.saga.pending").gauge().value());
        assertEquals(4.0, registry.get("gm.quality.human.score").summary().totalAmount());
    }
}
