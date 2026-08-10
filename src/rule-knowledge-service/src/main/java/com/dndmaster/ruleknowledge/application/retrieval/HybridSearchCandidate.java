package com.dndmaster.ruleknowledge.application.retrieval;

import java.util.Objects;
import java.util.UUID;

public record HybridSearchCandidate(UUID evidenceId, double denseScore, double lexicalScore, double metadataScore) {
    public HybridSearchCandidate {
        Objects.requireNonNull(evidenceId, "evidenceId must not be null");
        if (!Double.isFinite(denseScore) || !Double.isFinite(lexicalScore) || !Double.isFinite(metadataScore)) {
            throw new IllegalArgumentException("scores must be finite");
        }
    }
}
