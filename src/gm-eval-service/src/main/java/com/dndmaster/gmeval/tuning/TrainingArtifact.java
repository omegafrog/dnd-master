package com.dndmaster.gmeval.tuning;

import com.dndmaster.gmeval.registry.PromptRole;
import java.util.Objects;

/** Immutable provider output and reproducibility identity for one training run. */
public record TrainingArtifact(String artifactId, String proposalId, PromptRole role,
                               String baseModelVersion, String optimizedPromptVersion, String tunedModelVersion,
                               String datasetVersion, String holdoutVersion, TrainingHyperparameters hyperparameters,
                               String providerVersion, String artifactUri) {
    public TrainingArtifact {
        artifactId = required(artifactId, "training artifact id");
        proposalId = required(proposalId, "proposal id");
        role = Objects.requireNonNull(role, "training role required");
        baseModelVersion = required(baseModelVersion, "base model version");
        optimizedPromptVersion = required(optimizedPromptVersion, "optimized prompt version");
        tunedModelVersion = required(tunedModelVersion, "tuned model version");
        datasetVersion = required(datasetVersion, "training dataset version");
        holdoutVersion = required(holdoutVersion, "holdout version");
        hyperparameters = Objects.requireNonNull(hyperparameters, "training hyperparameters required");
        providerVersion = required(providerVersion, "provider version");
        artifactUri = required(artifactUri, "artifact uri");
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " required");
        return value.trim();
    }
}
