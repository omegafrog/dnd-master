package com.dndmaster.gmeval.tuning;

/** Adoption thresholds; hard safety always has precedence over quality. */
public record TuningGatePolicy(double minimumSoftImprovement, double minimumHoldoutSoftImprovement,
                               long maximumCostMicros, long maximumLatencyMillis) {
    public TuningGatePolicy {
        if (!Double.isFinite(minimumSoftImprovement) || !Double.isFinite(minimumHoldoutSoftImprovement)) {
            throw new IllegalArgumentException("soft thresholds must be finite");
        }
        if (maximumCostMicros < 0) throw new IllegalArgumentException("maximum cost must be non-negative");
        if (maximumLatencyMillis < 0) throw new IllegalArgumentException("maximum latency must be non-negative");
    }
}
