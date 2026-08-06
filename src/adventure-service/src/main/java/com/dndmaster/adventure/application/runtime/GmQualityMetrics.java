package com.dndmaster.adventure.application.runtime;

public interface GmQualityMetrics {
    void record(GmQualityGateReport report);
    default void recordContextUsage(ContextUsage usage) { }
    default void recordSagaPending() { }
    default void recordSagaCompleted() { }
}
