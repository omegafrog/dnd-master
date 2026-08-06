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
        List<HybridRetrievalCandidate> related = candidates.stream().filter(scope::accepts)
                .filter(candidate -> candidate.documentId().equals(seed.documentId())
                        && candidate.extractionVersion() == seed.extractionVersion())
                .sorted(java.util.Comparator.comparing(CandidateWindowContextExpansion::locatorOrder)
                        .thenComparing(HybridRetrievalCandidate::key)).toList();
        int index = java.util.stream.IntStream.range(0, related.size())
                .filter(candidateIndex -> related.get(candidateIndex).key().equals(seed.key())).findFirst().orElse(-1);
        if (index < 0) return List.of(seed);
        final int seedIndex = index;
        return related.stream()
                .filter(candidate -> Math.abs(related.indexOf(candidate) - seedIndex) <= radius)
                .sorted(java.util.Comparator.comparingInt(candidate -> candidate.key().equals(seed.key()) ? 0
                        : Math.abs(related.indexOf(candidate) - seedIndex)))
                .toList();
    }

    private static String locatorOrder(HybridRetrievalCandidate candidate) {
        String locator = candidate.locator();
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(\\d+)(?!.*\\d)").matcher(locator);
        if (!matcher.find()) return "1:" + locator;
        return "0:" + String.format("%020d", Long.parseLong(matcher.group(1))) + ":" + locator;
    }
}
