package com.dndmaster.adventure.application.runtime;

import java.util.Objects;

/** Runtime-safe projection of one approved, active gm-eval role configuration. */
public record ApprovedPromptConfiguration(String role, String promptVersion, String modelVersion,
                                          String optimizationRunId, String parentPromptVersion,
                                          String datasetVersion, String evalVersion, long activationVersion) {
    public ApprovedPromptConfiguration {
        role = required(role, "prompt role").toUpperCase(java.util.Locale.ROOT);
        promptVersion = required(promptVersion, "prompt version");
        modelVersion = required(modelVersion, "model version");
        if (activationVersion < 1) throw new IllegalArgumentException("activation version must be positive");
        optional(optimizationRunId, "optimization run id");
        optional(parentPromptVersion, "parent prompt version");
        optional(datasetVersion, "dataset version");
        optional(evalVersion, "eval version");
    }

    public EffectivePromptLineage lineage() {
        return new EffectivePromptLineage(role, promptVersion, modelVersion, optimizationRunId,
                parentPromptVersion, datasetVersion, evalVersion);
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value.trim();
    }
    private static void optional(String value, String name) {
        if (value != null && value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
    }
}
