package com.dndmaster.ruleknowledge.application.search;

import com.dndmaster.ruleknowledge.domain.rulebook.OwnerPlayerId;
import java.util.List;
import java.util.Objects;

/** Input for a single scoped dense and BM25 candidate search. */
public record EvidenceSearchRequest(
        OwnerPlayerId ownerPlayerId,
        List<AuthorizedDocumentScope> scope,
        String query,
        int denseLimit,
        int bm25Limit) {
    public EvidenceSearchRequest {
        Objects.requireNonNull(ownerPlayerId, "owner player id must not be null");
        scope = List.copyOf(Objects.requireNonNull(scope, "scope must not be null"));
        if (scope.isEmpty() || scope.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("scope must contain authorized documents");
        }
        if (query == null || query.isBlank()) throw new IllegalArgumentException("query must not be blank");
        validateLimit(denseLimit, "dense limit");
        validateLimit(bm25Limit, "BM25 limit");
    }

    private static void validateLimit(int value, String name) {
        if (value < 1 || value > RrfFusionPolicy.MAX_RESULTS_PER_RETRIEVER) {
            throw new IllegalArgumentException(name + " must be between 1 and 30");
        }
    }
}
