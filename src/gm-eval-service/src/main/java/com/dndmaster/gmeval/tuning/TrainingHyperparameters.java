package com.dndmaster.gmeval.tuning;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Immutable training recipe retained with every produced artifact. */
public record TrainingHyperparameters(TuningMethod method, int epochs, int batchSize,
                                     double learningRate, long seed, Map<String, String> options) {
    public TrainingHyperparameters {
        method = Objects.requireNonNull(method, "training method required");
        if (epochs <= 0) throw new IllegalArgumentException("epochs must be positive");
        if (batchSize <= 0) throw new IllegalArgumentException("batch size must be positive");
        if (!Double.isFinite(learningRate) || learningRate <= 0) throw new IllegalArgumentException("learning rate must be positive");
        Map<String, String> copy = new LinkedHashMap<>();
        if (options != null) options.forEach((key, value) -> {
            if (key == null || key.isBlank() || value == null || value.isBlank()) throw new IllegalArgumentException("invalid training option");
            copy.put(key, value);
        });
        options = Collections.unmodifiableMap(copy);
    }
}
