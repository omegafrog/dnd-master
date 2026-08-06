package com.dndmaster.aigamemaster.benchmark.finetuning;

public record FineTuningMetrics(double qualityScore, double groundingRate, double koreanNarrationRate,
                                double structureSuccessRate, double latencyMeanMs, double latencyVarianceMs,
                                double costUsd) {
    public FineTuningMetrics {
        if (!finiteRate(qualityScore, 0, 5) || !finiteRate(groundingRate, 0, 1)
                || !finiteRate(koreanNarrationRate, 0, 1) || !finiteRate(structureSuccessRate, 0, 1)
                || !finiteRate(latencyMeanMs, 0, Double.MAX_VALUE) || !finiteRate(latencyVarianceMs, 0, Double.MAX_VALUE)
                || !finiteRate(costUsd, 0, Double.MAX_VALUE)) throw new IllegalArgumentException("invalid fine-tuning metrics");
    }

    private static boolean finiteRate(double value, double min, double max) {
        return Double.isFinite(value) && value >= min && value <= max;
    }
}
