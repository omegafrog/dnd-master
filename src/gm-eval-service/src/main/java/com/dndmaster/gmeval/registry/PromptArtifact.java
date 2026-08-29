package com.dndmaster.gmeval.registry;

import java.util.List;
import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonIgnore;

/** Immutable, reproducible prompt and generation configuration artifact. */
public record PromptArtifact(
        PromptVersion promptVersion,
        PromptVersion parentVersion,
        String promptContent,
        String outputSchema,
        List<String> contextOrdering,
        String exemplarPlacement,
        String modelVersion,
        String configurationVersion,
        String datasetVersion,
        String evalVersion,
        boolean baseline,
        PromptArtifactStatus status) {
    public PromptArtifact {
        promptVersion = Objects.requireNonNull(promptVersion, "prompt version required");
        if (parentVersion != null && parentVersion.role() != promptVersion.role()) {
            throw new IllegalArgumentException("parent prompt must belong to the same role");
        }
        promptContent = required(promptContent, "prompt content");
        outputSchema = required(outputSchema, "output schema");
        contextOrdering = List.copyOf(contextOrdering == null ? List.of() : contextOrdering);
        if (contextOrdering.isEmpty() || contextOrdering.stream().anyMatch(value -> value == null || value.isBlank())) {
            throw new IllegalArgumentException("context ordering required");
        }
        exemplarPlacement = required(exemplarPlacement, "exemplar placement");
        modelVersion = required(modelVersion, "model version");
        configurationVersion = required(configurationVersion, "configuration version");
        datasetVersion = required(datasetVersion, "dataset version");
        evalVersion = required(evalVersion, "eval version");
        status = Objects.requireNonNull(status, "artifact status required");
    }

    @JsonIgnore
    public boolean isApproved() {
        return status == PromptArtifactStatus.APPROVED || status == PromptArtifactStatus.ACTIVE;
    }

    public PromptArtifact withStatus(PromptArtifactStatus nextStatus) {
        return new PromptArtifact(promptVersion, parentVersion, promptContent, outputSchema, contextOrdering,
                exemplarPlacement, modelVersion, configurationVersion, datasetVersion, evalVersion, baseline, nextStatus);
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " required");
        return value;
    }
}
