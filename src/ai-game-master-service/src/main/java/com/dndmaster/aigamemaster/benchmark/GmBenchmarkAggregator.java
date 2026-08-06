package com.dndmaster.aigamemaster.benchmark;

import java.util.Comparator;
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
        double variance = java.util.Arrays.stream(latency).map(value -> Math.pow(value - mean, 2)).average().orElseThrow();
        java.util.Arrays.sort(latency);
        return new GmBenchmarkMetrics(runs.size(), rate(runs, GmBenchmarkRun::structuredSuccess),
                rate(runs, GmBenchmarkRun::secretLeak), rate(runs, GmBenchmarkRun::citationCorrect), mean,
                variance, percentile(latency, 0.50), percentile(latency, 0.95));
    }

    private static double rate(List<GmBenchmarkRun> runs, java.util.function.Predicate<GmBenchmarkRun> predicate) {
        return runs.stream().filter(predicate).count() / (double) runs.size();
    }

    private static double percentile(double[] values, double percentile) {
        if (values.length == 1) return values[0];
        double position = percentile * (values.length - 1);
        int lower = (int) Math.floor(position);
        int upper = (int) Math.ceil(position);
        return values[lower] + (values[upper] - values[lower]) * (position - lower);
    }
}
