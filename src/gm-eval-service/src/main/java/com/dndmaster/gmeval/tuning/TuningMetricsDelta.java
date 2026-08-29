package com.dndmaster.gmeval.tuning;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Candidate minus base deltas, including operational dimensions. */
public record TuningMetricsDelta(Map<String, Integer> hardViolationDelta,
                                 Map<String, Double> softScoreDelta,
                                 long costDeltaMicros, long latencyDeltaMillis,
                                 Set<TuningFailureCategory> resolvedFailureCategories,
                                 Set<TuningFailureCategory> introducedFailureCategories) {
    public TuningMetricsDelta {
        Map<String, Integer> hard = new LinkedHashMap<>();
        if (hardViolationDelta != null) hardViolationDelta.forEach((key, value) -> {
            if (key == null || key.isBlank() || value == null) throw new IllegalArgumentException("invalid hard metric delta");
            hard.put(key, value);
        });
        hardViolationDelta = Collections.unmodifiableMap(hard);
        Map<String, Double> soft = new LinkedHashMap<>();
        if (softScoreDelta != null) softScoreDelta.forEach((key, value) -> {
            if (key == null || key.isBlank() || value == null || !Double.isFinite(value)) throw new IllegalArgumentException("invalid soft metric delta");
            soft.put(key, value);
        });
        softScoreDelta = Collections.unmodifiableMap(soft);
        resolvedFailureCategories = resolvedFailureCategories == null ? Set.of() : Set.copyOf(resolvedFailureCategories);
        introducedFailureCategories = introducedFailureCategories == null ? Set.of() : Set.copyOf(introducedFailureCategories);
    }

    public static TuningMetricsDelta between(TuningMetrics candidate, TuningMetrics baseline) {
        Objects.requireNonNull(candidate, "candidate metrics required");
        Objects.requireNonNull(baseline, "baseline metrics required");
        Map<String, Integer> hard = new LinkedHashMap<>();
        baseline.hardViolations().keySet().stream().sorted().forEach(metric ->
                hard.put(metric, candidate.hardViolations().getOrDefault(metric, 0) - baseline.hardViolations().get(metric)));
        candidate.hardViolations().keySet().stream().sorted().filter(metric -> !hard.containsKey(metric)).forEach(metric ->
                hard.put(metric, candidate.hardViolations().get(metric)));
        Map<String, Double> soft = new LinkedHashMap<>();
        baseline.softScores().keySet().stream().sorted().forEach(metric ->
                soft.put(metric, candidate.softScores().getOrDefault(metric, 0.0) - baseline.softScores().get(metric)));
        candidate.softScores().keySet().stream().sorted().filter(metric -> !soft.containsKey(metric)).forEach(metric ->
                soft.put(metric, candidate.softScores().get(metric)));
        Set<TuningFailureCategory> resolved = new java.util.HashSet<>(baseline.failureTaxonomy());
        resolved.removeAll(candidate.failureTaxonomy());
        Set<TuningFailureCategory> introduced = new java.util.HashSet<>(candidate.failureTaxonomy());
        introduced.removeAll(baseline.failureTaxonomy());
        return new TuningMetricsDelta(hard, soft, candidate.costMicros() - baseline.costMicros(),
                candidate.latencyMillis() - baseline.latencyMillis(), resolved, introduced);
    }
}
