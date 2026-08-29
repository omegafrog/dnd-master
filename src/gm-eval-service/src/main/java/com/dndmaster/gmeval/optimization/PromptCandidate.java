package com.dndmaster.gmeval.optimization;

import com.dndmaster.gmeval.registry.PromptArtifact;
import java.util.Objects;

/** Role-scoped immutable candidate; its prompt artifact is the complete input identity. */
public record PromptCandidate(String candidateId, PromptArtifact promptArtifact, long seed) {
    public PromptCandidate {
        if (candidateId == null || candidateId.isBlank()) throw new IllegalArgumentException("candidate id required");
        promptArtifact = Objects.requireNonNull(promptArtifact, "prompt artifact required");
        if (promptArtifact.baseline()) throw new IllegalArgumentException("baseline is not a candidate");
        candidateId = candidateId.trim();
    }

    public com.dndmaster.gmeval.registry.PromptRole role() {
        return promptArtifact.promptVersion().role();
    }
}
