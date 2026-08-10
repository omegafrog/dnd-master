package com.dndmaster.ruleknowledge.application.retrieval;

import com.dndmaster.ruleknowledge.application.search.QueryIntent;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Merges independently normalized dense, lexical, and metadata scores. */
public final class HybridRetrievalService {
    public List<RankedCandidate> rerank(List<HybridSearchCandidate> candidates, QueryIntent intent, int limit) {
        Objects.requireNonNull(candidates, "candidates must not be null");
        Objects.requireNonNull(intent, "intent must not be null");
        if (limit <= 0) throw new IllegalArgumentException("limit must be positive");
        Weights weights = Weights.forIntent(intent);
        return candidates.stream()
                .map(candidate -> new RankedCandidate(candidate.evidenceId(),
                        weights.dense() * candidate.denseScore()
                                + weights.lexical() * candidate.lexicalScore()
                                + weights.metadata() * candidate.metadataScore()))
                .sorted(Comparator.comparingDouble(RankedCandidate::score).reversed()
                        .thenComparing(result -> result.evidenceId().toString()))
                .limit(limit)
                .toList();
    }

    public record RankedCandidate(java.util.UUID evidenceId, double score) {}

    private record Weights(double dense, double lexical, double metadata) {
        static Weights forIntent(QueryIntent intent) {
            return switch (intent) {
                case RULE -> new Weights(0.55, 0.35, 0.10);
                case STORY -> new Weights(0.45, 0.35, 0.20);
                case MIXED -> new Weights(0.50, 0.30, 0.20);
                case UNKNOWN -> new Weights(0.40, 0.30, 0.30);
            };
        }
    }
}
