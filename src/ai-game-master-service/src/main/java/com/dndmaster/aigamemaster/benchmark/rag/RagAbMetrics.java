package com.dndmaster.aigamemaster.benchmark.rag;

import java.util.List;

public record RagAbMetrics(int runs, double ruleAccuracy, double citationAccuracy, double hallucinationRate,
        double secretLeakRate, double prematureStateChangeRate, double continuityAccuracy,
        double structureSuccessRate, double humanScoreMean, double humanScoreVariance,
        double latencyMeanMs, double latencyVarianceMs, double latencyP50Ms, double latencyP95Ms,
        double retrievalRecallMean, double costMeanUsd) {
    static RagAbMetrics aggregate(List<RagAbExecution> values) {
        if (values == null || values.isEmpty()) throw new IllegalArgumentException("RAG A/B runs required");
        double[] latency = values.stream().mapToDouble(RagAbExecution::latencyMs).sorted().toArray();
        return new RagAbMetrics(values.size(), rate(values, RagAbExecution::ruleAccurate), rate(values, RagAbExecution::citationCorrect),
                rate(values, RagAbExecution::hallucination), rate(values, RagAbExecution::secretLeak),
                rate(values, RagAbExecution::prematureStateChange), rate(values, RagAbExecution::continuityCorrect),
                rate(values, RagAbExecution::structuredSuccess), mean(values, RagAbExecution::humanScore), variance(values, RagAbExecution::humanScore),
                mean(values, RagAbExecution::latencyMs), variance(values, RagAbExecution::latencyMs), percentile(latency, .5), percentile(latency, .95),
                mean(values, RagAbExecution::retrievalRecall), mean(values, RagAbExecution::costUsd));
    }
    private static double rate(List<RagAbExecution> values, java.util.function.Predicate<RagAbExecution> p) { return values.stream().filter(p).count() / (double) values.size(); }
    private static double mean(List<RagAbExecution> values, java.util.function.ToDoubleFunction<RagAbExecution> f) { return values.stream().mapToDouble(f).average().orElseThrow(); }
    private static double variance(List<RagAbExecution> values, java.util.function.ToDoubleFunction<RagAbExecution> f) { double mean = mean(values, f); return values.stream().mapToDouble(v -> Math.pow(f.applyAsDouble(v) - mean, 2)).average().orElseThrow(); }
    private static double percentile(double[] values, double p) { if (values.length == 1) return values[0]; double x = p * (values.length - 1); int lo = (int) Math.floor(x), hi = (int) Math.ceil(x); return values[lo] + (values[hi] - values[lo]) * (x - lo); }
}
