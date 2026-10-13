package com.dndmaster.ruleknowledge.application.search;

import java.util.List;
import java.util.Objects;

/** The candidates available to the next evidence-processing step. */
public record EvidenceSearchResult(List<EvidenceCandidate> candidates) {
    public EvidenceSearchResult {
        candidates = List.copyOf(Objects.requireNonNull(candidates, "candidates must not be null"));
        if (candidates.size() > 64) {
            throw new IllegalArgumentException("candidates must contain at most 64 items");
        }
    }
}
