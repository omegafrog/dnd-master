package com.dndmaster.adventure.infrastructure.metrics;

import com.dndmaster.adventure.application.runtime.GmQualityGateReport;
import com.dndmaster.adventure.application.runtime.GmQualityMetrics;
import com.dndmaster.adventure.application.runtime.ContextUsage;
import io.micrometer.core.instrument.MeterRegistry;

public final class MicrometerGmQualityMetrics implements GmQualityMetrics {
    private final MeterRegistry registry;
    public MicrometerGmQualityMetrics(MeterRegistry registry) { this.registry = registry; }

    @Override public void record(GmQualityGateReport report) {
        registry.counter("gm.quality.gate", "result", report.passed() ? "pass" : "fail").increment();
        registry.counter("gm.quality.secret.violations").increment(report.secretViolations());
        registry.counter("gm.quality.forbidden_tool.violations").increment(report.forbiddenToolViolations());
        registry.counter("gm.quality.invented_state.violations").increment(report.inventedStateViolations());
        registry.summary("gm.quality.human.score").record(report.humanScore());
    }
    @Override public void recordContextUsage(ContextUsage usage) {
        registry.summary("gm.context.utilization").record((double) usage.estimatedTokens() / usage.contextLimit());
    }
    @Override public void recordSagaPending() { registry.counter("gm.saga.pending").increment(); }
    @Override public void recordSagaCompleted() { registry.counter("gm.saga.completed").increment(); }
}
