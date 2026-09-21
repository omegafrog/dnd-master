package com.dndmaster.ruleknowledge.application.search;

import java.util.List;
import java.util.Objects;

/** The unique RRF-fused candidates available to the next evidence-processing step. */
public record EvidenceSearchResult(List<EvidenceCandidate> candidates) {
    public EvidenceSearchResult {
        candidates = List.copyOf(Objects.requireNonNull(candidates, "candidates must not be null"));
        if (candidates.size() > RrfFusionPolicy.MAX_UNIQUE_CANDIDATES) {
            throw new IllegalArgumentException("candidates must contain at most 60 items");
        }
    }
}
