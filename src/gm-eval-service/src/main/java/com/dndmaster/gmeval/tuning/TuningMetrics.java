package com.dndmaster.gmeval.tuning;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record TuningMetrics(Map<String, Integer> hardViolations, Map<String, Double> softScores) {
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
    }
}
