package com.dndmaster.adventure.application.runtime;

/** Immutable provider identity captured on the resolved turn actually used at runtime. */
public record EffectivePromptLineage(String role, String promptVersion, String modelVersion,
                                     String optimizationRunId, String parentPromptVersion) {
    public EffectivePromptLineage {
        required(role, "prompt role");
        required(promptVersion, "prompt version");
        required(modelVersion, "model version");
        optional(optimizationRunId, "optimization run id");
        optional(parentPromptVersion, "parent prompt version");
    }

    private static void required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
    }

    private static void optional(String value, String name) {
        if (value != null && value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
    }
}
