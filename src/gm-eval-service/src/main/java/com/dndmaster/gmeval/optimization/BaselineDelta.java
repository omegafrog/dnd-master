package com.dndmaster.gmeval.optimization;

import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Candidate minus baseline, retained in reports for audit and reproducibility. */
public record BaselineDelta(Map<HardMetric, Integer> hardViolationDelta, Map<String, Double> softScoreDelta) {
    public BaselineDelta {
        EnumMap<HardMetric, Integer> hard = new EnumMap<>(HardMetric.class);
        if (hardViolationDelta != null) hard.putAll(hardViolationDelta);
        for (HardMetric metric : HardMetric.values()) hard.putIfAbsent(metric, 0);
        hardViolationDelta = Collections.unmodifiableMap(hard);
        Map<String, Double> soft = new LinkedHashMap<>();
        if (softScoreDelta != null) {
            softScoreDelta.forEach((dimension, value) -> {
                if (dimension == null || dimension.isBlank() || value == null || !Double.isFinite(value)) {
                    throw new IllegalArgumentException("invalid soft metric delta");
                }
                soft.put(dimension, value);
            });
        }
        softScoreDelta = Collections.unmodifiableMap(soft);
    }

    public static BaselineDelta between(MetricVector candidate, MetricVector baseline) {
        Objects.requireNonNull(candidate, "candidate metrics required");
        Objects.requireNonNull(baseline, "baseline metrics required");
        EnumMap<HardMetric, Integer> hard = new EnumMap<>(HardMetric.class);
        for (HardMetric metric : HardMetric.values()) hard.put(metric, candidate.hard(metric) - baseline.hard(metric));
        Map<String, Double> soft = new LinkedHashMap<>();
        candidate.softScores().keySet().stream().sorted().forEach(dimension ->
                soft.put(dimension, candidate.softScores().getOrDefault(dimension, 0.0)
                        - baseline.softScores().getOrDefault(dimension, 0.0)));
        baseline.softScores().keySet().stream().sorted().filter(dimension -> !soft.containsKey(dimension)).forEach(dimension ->
                soft.put(dimension, -baseline.softScores().get(dimension)));
        return new BaselineDelta(hard, soft);
    }

    public int hard(HardMetric metric) {
        return hardViolationDelta.getOrDefault(Objects.requireNonNull(metric), 0);
    }
}
