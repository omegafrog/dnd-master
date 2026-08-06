package com.dndmaster.ruleknowledge.application.search;

import java.util.List;
import java.util.Objects;

/** Expands within the already retrieved, scope-filtered window. */
public final class CandidateWindowContextExpansion implements ContextExpansionPort {
    private final List<HybridRetrievalCandidate> candidates;

    public CandidateWindowContextExpansion(List<HybridRetrievalCandidate> candidates) {
        this.candidates = List.copyOf(Objects.requireNonNull(candidates));
    }

    @Override
    public List<HybridRetrievalCandidate> expand(HybridRetrievalCandidate seed, RetrievalScope scope, int radius) {
        int index = -1;
        for (int candidateIndex = 0; candidateIndex < candidates.size(); candidateIndex++) {
            if (candidates.get(candidateIndex).key().equals(seed.key())) {
                index = candidateIndex;
                break;
            }
        }
        if (index < 0) return List.of(seed);
        final int seedIndex = index;
        return candidates.stream()
                .filter(scope::accepts)
                .filter(candidate -> candidate.documentId().equals(seed.documentId())
                        && candidate.extractionVersion() == seed.extractionVersion())
                .filter(candidate -> Math.abs(candidates.indexOf(candidate) - seedIndex) <= radius)
                .sorted(java.util.Comparator.comparingInt(candidate -> candidate.key().equals(seed.key()) ? 0
                        : Math.abs(candidates.indexOf(candidate) - seedIndex)))
                .toList();
    }
}
