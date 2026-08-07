package com.dndmaster.aigamemaster.benchmark;

public record GmBenchmarkPhaseMetrics(double meanMs, double p50Ms, double p95Ms) {
    public GmBenchmarkPhaseMetrics {
        if (!finiteNonNegative(meanMs) || !finiteNonNegative(p50Ms) || !finiteNonNegative(p95Ms)) {
            throw new IllegalArgumentException("invalid phase latency metrics");
        }
    }

    private static boolean finiteNonNegative(double value) {
        return Double.isFinite(value) && value >= 0;
    }
}
