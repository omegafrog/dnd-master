package com.dndmaster.aigamemaster.benchmark;

import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Objects;

public final class GmBenchmarkAggregator {
    private GmBenchmarkAggregator() {}

    public static GmBenchmarkMetrics aggregate(List<GmBenchmarkRun> runs) {
        Objects.requireNonNull(runs);
        if (runs.isEmpty()) throw new IllegalArgumentException("benchmark runs required");
        if (runs.stream().map(GmBenchmarkRun::caseId).distinct().count() != 1) {
            throw new IllegalArgumentException("aggregate one case at a time");
        }
        return aggregateAll(runs);
    }

    public static GmBenchmarkMetrics aggregateAll(List<GmBenchmarkRun> runs) {
        Objects.requireNonNull(runs);
        if (runs.isEmpty()) throw new IllegalArgumentException("benchmark runs required");
        double[] latency = runs.stream().mapToDouble(GmBenchmarkRun::latencyMs).toArray();
        for (double value : latency) if (!Double.isFinite(value)) throw new IllegalArgumentException("non-finite metric");
        double mean = java.util.Arrays.stream(latency).average().orElseThrow();
        double variance = java.util.Arrays.stream(latency).map(value -> Math.pow(value - mean, 2)).sum()
                / Math.max(1, latency.length - 1);
        java.util.Arrays.sort(latency);
        var phases = new EnumMap<GmBenchmarkPhase, GmBenchmarkPhaseMetrics>(GmBenchmarkPhase.class);
        for (var phase : GmBenchmarkPhase.values()) {
            double[] values = runs.stream().mapToDouble(run -> phaseValue(run.timing(), phase)).toArray();
            phases.put(phase, phaseMetrics(values));
        }
        return new GmBenchmarkMetrics(runs.size(), rate(runs, GmBenchmarkRun::structuredSuccess),
                rate(runs, GmBenchmarkRun::secretLeak), rate(runs, GmBenchmarkRun::citationCorrect), mean,
                variance, percentile(latency, 0.50), percentile(latency, 0.95), phases);
    }

    private static double rate(List<GmBenchmarkRun> runs, java.util.function.Predicate<GmBenchmarkRun> predicate) {
        return runs.stream().filter(predicate).count() / (double) runs.size();
    }

    private static double percentile(double[] values, double percentile) {
        int rank = (int) Math.ceil(percentile * values.length);
        return values[Math.max(0, Math.min(values.length - 1, rank - 1))];
    }

    private static GmBenchmarkPhaseMetrics phaseMetrics(double[] values) {
        double mean = java.util.Arrays.stream(values).average().orElseThrow();
        java.util.Arrays.sort(values);
        return new GmBenchmarkPhaseMetrics(mean, percentile(values, .50), percentile(values, .95));
    }

    private static double phaseValue(GmBenchmarkTiming timing, GmBenchmarkPhase phase) {
        return switch (phase) {
            case TTFT -> timing.ttftMs();
            case COMPLETION -> timing.completionMs();
            case RETRIEVAL -> timing.retrievalMs();
            case REPAIR_INCLUSIVE -> timing.repairInclusiveMs();
            case END_TO_END -> timing.endToEndMs();
        };
    }
}
