package com.dndmaster.ruleknowledge.application.search;

import java.util.Comparator;
import java.util.List;

@FunctionalInterface
public interface Reranker {
    List<HybridRetrievalCandidate> rerank(String query, List<HybridRetrievalCandidate> candidates);

    static Reranker deterministic() {
        return (query, candidates) -> candidates.stream()
                .map(candidate -> candidate.withScore(.65d * candidate.denseScore()
                        + .35d * candidate.keywordScore()))
                .sorted(Comparator.comparingDouble(HybridRetrievalCandidate::score).reversed()
                        .thenComparing(HybridRetrievalCandidate::key))
                .toList();
    }
}
