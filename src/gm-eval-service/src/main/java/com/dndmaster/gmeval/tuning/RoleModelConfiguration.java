package com.dndmaster.gmeval.tuning;

import com.dndmaster.gmeval.registry.PromptRole;
import com.dndmaster.gmeval.registry.PromptVersion;
import java.util.Objects;

/** Runtime model binding; one independent binding exists per prompt role. */
public record RoleModelConfiguration(PromptRole role, String modelVersion, PromptVersion promptVersion,
                                     String trainingArtifactId, String evaluationId,
                                     TuningLineageDeltaReport lineageDelta) {
    public RoleModelConfiguration {
        role = Objects.requireNonNull(role, "model role required");
        modelVersion = required(modelVersion, "model version");
        promptVersion = Objects.requireNonNull(promptVersion, "prompt version required");
        if (promptVersion.role() != role) throw new IllegalArgumentException("model prompt role mismatch");
        trainingArtifactId = required(trainingArtifactId, "training artifact id");
        if (evaluationId != null && evaluationId.isBlank()) evaluationId = null;
        if (lineageDelta != null && lineageDelta.role() != role) throw new IllegalArgumentException("lineage role mismatch");
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " required");
        return value.trim();
    }
}
