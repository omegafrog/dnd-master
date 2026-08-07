package com.dndmaster.aigamemaster.benchmark.finetuning;

import java.util.List;
import java.util.Objects;

public record FineTuningMetrics(double qualityScore, double groundingRate, double koreanNarrationRate,
                                double structureSuccessRate, double latencyMeanMs, double latencyVarianceMs,
                                double costUsd, double qualityVariance, int sampleCount,
                                double secretLeakRate, double stateConsistencyRate, double scopeComplianceRate,
                                List<Double> qualitySamples) {
    public FineTuningMetrics(double qualityScore, double groundingRate, double koreanNarrationRate,
                             double structureSuccessRate, double latencyMeanMs, double latencyVarianceMs,
                             double costUsd, double qualityVariance, int sampleCount) {
        this(qualityScore, groundingRate, koreanNarrationRate, structureSuccessRate, latencyMeanMs,
                latencyVarianceMs, costUsd, qualityVariance, sampleCount, 0, 1, 1,
                java.util.Collections.nCopies(sampleCount, qualityScore));
    }

    public FineTuningMetrics {
        Objects.requireNonNull(qualitySamples, "quality samples");
        qualitySamples = List.copyOf(qualitySamples);
        double sampleMean = qualitySamples.stream().mapToDouble(Double::doubleValue).average().orElse(Double.NaN);
        double sampleVariance = qualitySamples.stream().mapToDouble(value -> Math.pow(value - sampleMean, 2)).average().orElse(Double.NaN);
        if (!finiteRate(qualityScore, 0, 5) || !finiteRate(groundingRate, 0, 1)
                || !finiteRate(koreanNarrationRate, 0, 1) || !finiteRate(structureSuccessRate, 0, 1)
                || !finiteRate(latencyMeanMs, 0, Double.MAX_VALUE) || !finiteRate(latencyVarianceMs, 0, Double.MAX_VALUE)
                || !finiteRate(costUsd, 0, Double.MAX_VALUE) || !finiteRate(qualityVariance, 0, Double.MAX_VALUE)
                || !finiteRate(secretLeakRate, 0, 1) || !finiteRate(stateConsistencyRate, 0, 1)
                || !finiteRate(scopeComplianceRate, 0, 1) || sampleCount < 2
                || qualitySamples.size() != sampleCount
                || qualitySamples.stream().anyMatch(value -> !finiteRate(value, 0, 5))
                || Math.abs(sampleMean - qualityScore) > 1e-9
                || Math.abs(sampleVariance - qualityVariance) > 1e-9) {
            throw new IllegalArgumentException("invalid fine-tuning metrics");
        }
    }

    private static boolean finiteRate(double value, double min, double max) {
        return Double.isFinite(value) && value >= min && value <= max;
    }
}
