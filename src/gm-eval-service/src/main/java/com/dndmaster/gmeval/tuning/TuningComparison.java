package com.dndmaster.gmeval.tuning;

import com.dndmaster.gmeval.registry.PromptRole;
import java.util.Objects;

/** Fixed comparison identity for the later tuning evaluation; no runtime activation here. */
public record TuningComparison(PromptRole role, String baseModelVersion, String optimizedPromptVersion,
                               String tunedModelVersion, String evalVersion, String holdoutVersion,
                               TuningMetrics baselineMetrics, TuningMetrics tunedMetrics) {
    public TuningComparison {
        role = Objects.requireNonNull(role, "comparison role required");
        baseModelVersion = required(baseModelVersion, "base model version");
        optimizedPromptVersion = required(optimizedPromptVersion, "optimized prompt version");
        tunedModelVersion = required(tunedModelVersion, "tuned model version");
        evalVersion = required(evalVersion, "comparison eval version");
        holdoutVersion = required(holdoutVersion, "comparison holdout version");
        baselineMetrics = Objects.requireNonNull(baselineMetrics, "baseline metrics required");
        tunedMetrics = Objects.requireNonNull(tunedMetrics, "tuned metrics required");
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " required");
        return value.trim();
    }
}
