package com.dndmaster.gmeval.optimization;

import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Immutable hard-violation counts and soft quality scores for one evaluation. */
public record MetricVector(Map<HardMetric, Integer> hardViolations, Map<String, Double> softScores) {
    public MetricVector {
        EnumMap<HardMetric, Integer> hard = new EnumMap<>(HardMetric.class);
        if (hardViolations != null) {
            hardViolations.forEach((metric, value) -> {
                Objects.requireNonNull(metric, "hard metric required");
                if (value == null || value < 0) throw new IllegalArgumentException("hard metric count must be non-negative");
                hard.put(metric, value);
            });
        }
        for (HardMetric metric : HardMetric.values()) hard.putIfAbsent(metric, 0);
        hardViolations = Collections.unmodifiableMap(hard);

        Map<String, Double> soft = new LinkedHashMap<>();
        if (softScores != null) {
            softScores.forEach((dimension, value) -> {
                if (dimension == null || dimension.isBlank() || value == null || !Double.isFinite(value)) {
                    throw new IllegalArgumentException("invalid soft metric");
                }
                soft.put(dimension, value);
            });
        }
        softScores = Collections.unmodifiableMap(soft);
    }

    public int hard(HardMetric metric) {
        return hardViolations.getOrDefault(Objects.requireNonNull(metric), 0);
    }

    public int totalHardViolations() {
        return hardViolations.values().stream().mapToInt(Integer::intValue).sum();
    }

    public double softScore() {
        return softScores.values().stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
    }

    /** Keeps the worst hard result while retaining only the selection-phase soft scores. */
    public static MetricVector worstHard(MetricVector first, MetricVector second) {
        Objects.requireNonNull(first, "first metrics required");
        Objects.requireNonNull(second, "second metrics required");
        EnumMap<HardMetric, Integer> hard = new EnumMap<>(HardMetric.class);
        for (HardMetric metric : HardMetric.values()) hard.put(metric, Math.max(first.hard(metric), second.hard(metric)));
        return new MetricVector(hard, second.softScores());
    }
}
