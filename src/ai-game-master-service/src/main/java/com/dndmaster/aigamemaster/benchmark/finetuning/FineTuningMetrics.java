package com.dndmaster.aigamemaster.benchmark.finetuning;

public record FineTuningMetrics(double qualityScore, double groundingRate, double koreanNarrationRate,
                                double structureSuccessRate, double latencyMeanMs, double latencyVarianceMs,
                                double costUsd, double qualityVariance, int sampleCount) {
    public FineTuningMetrics(double qualityScore, double groundingRate, double koreanNarrationRate,
                             double structureSuccessRate, double latencyMeanMs, double latencyVarianceMs,
                             double costUsd) {
        this(qualityScore, groundingRate, koreanNarrationRate, structureSuccessRate, latencyMeanMs,
                latencyVarianceMs, costUsd, .25, 3);
    }

    public FineTuningMetrics {
        if (!finiteRate(qualityScore, 0, 5) || !finiteRate(groundingRate, 0, 1)
                || !finiteRate(koreanNarrationRate, 0, 1) || !finiteRate(structureSuccessRate, 0, 1)
                || !finiteRate(latencyMeanMs, 0, Double.MAX_VALUE) || !finiteRate(latencyVarianceMs, 0, Double.MAX_VALUE)
                || !finiteRate(costUsd, 0, Double.MAX_VALUE) || !finiteRate(qualityVariance, 0, Double.MAX_VALUE)
                || sampleCount < 2) throw new IllegalArgumentException("invalid fine-tuning metrics");
    }

    private static boolean finiteRate(double value, double min, double max) {
        return Double.isFinite(value) && value >= min && value <= max;
    }
}
