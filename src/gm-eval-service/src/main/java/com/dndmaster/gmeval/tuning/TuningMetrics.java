package com.dndmaster.gmeval.tuning;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public record TuningMetrics(Map<String, Integer> hardViolations, Map<String, Double> softScores,
                            long costMicros, long latencyMillis, Set<TuningFailureCategory> failureTaxonomy) {
    public TuningMetrics(Map<String, Integer> hardViolations, Map<String, Double> softScores) {
        this(hardViolations, softScores, 0, 0, Set.of());
    }

    public TuningMetrics(Map<String, Integer> hardViolations, Map<String, Double> softScores,
                         long costMicros, long latencyMillis) {
        this(hardViolations, softScores, costMicros, latencyMillis, Set.of());
    }

    public TuningMetrics {
        Map<String, Integer> hard = new LinkedHashMap<>();
        if (hardViolations != null) hardViolations.forEach((key, value) -> {
            if (key == null || key.isBlank() || value == null || value < 0) throw new IllegalArgumentException("invalid hard metric");
            hard.put(key, value);
        });
        hardViolations = Collections.unmodifiableMap(hard);
        Map<String, Double> soft = new LinkedHashMap<>();
        if (softScores != null) softScores.forEach((key, value) -> {
            if (key == null || key.isBlank() || value == null || !Double.isFinite(value)) throw new IllegalArgumentException("invalid soft metric");
            soft.put(key, value);
        });
        softScores = Collections.unmodifiableMap(soft);
        if (costMicros < 0) throw new IllegalArgumentException("cost must be non-negative");
        if (latencyMillis < 0) throw new IllegalArgumentException("latency must be non-negative");
        failureTaxonomy = failureTaxonomy == null ? Set.of() : Set.copyOf(failureTaxonomy);
    }

    public double softScore() {
        return softScores.values().stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
    }
}
