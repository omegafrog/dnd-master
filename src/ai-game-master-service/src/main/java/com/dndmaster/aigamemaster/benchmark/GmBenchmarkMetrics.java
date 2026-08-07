package com.dndmaster.aigamemaster.benchmark;

import java.util.Map;

public record GmBenchmarkMetrics(int runs, double structureSuccessRate, double leakRate,
                                 double citationRate, double latencyMeanMs, double latencyVarianceMs,
                                 double latencyP50Ms, double latencyP95Ms,
                                 Map<GmBenchmarkPhase, GmBenchmarkPhaseMetrics> phaseMetrics) {
    public GmBenchmarkMetrics {
        phaseMetrics = Map.copyOf(phaseMetrics);
    }
}
