package com.dndmaster.adventure.evidence;

import java.util.List;
import java.util.Objects;

public record EvidenceAcquisitionResult(List<EvidenceCandidate> candidates, SufficiencyDecision decision, int additionalSearches) {
    public EvidenceAcquisitionResult {
        candidates = List.copyOf(Objects.requireNonNull(candidates, "candidates must not be null"));
        Objects.requireNonNull(decision, "decision must not be null");
        if (additionalSearches < 0 || additionalSearches > 2) throw new IllegalArgumentException("additional searches must be from zero through two");
    }
}
