package com.dndmaster.ruleknowledge.application.search;

import com.dndmaster.ruleknowledge.domain.rulebook.OwnerPlayerId;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Input for a single scoped dense and BM25 candidate search. */
public record EvidenceSearchRequest(
        OwnerPlayerId ownerPlayerId,
        UUID sessionId,
        UUID scenarioPackageId,
        String stageKey,
        String actionIntent,
        List<AuthorizedDocumentScope> scope,
        List<String> activeLocators,
        String query,
        int denseLimit,
        int bm25Limit) {
    public EvidenceSearchRequest {
        Objects.requireNonNull(ownerPlayerId, "owner player id must not be null");
        Objects.requireNonNull(sessionId, "session id must not be null");
        Objects.requireNonNull(scenarioPackageId, "scenario package id must not be null");
        if (stageKey == null || stageKey.isBlank()) throw new IllegalArgumentException("stage key must not be blank");
        if (actionIntent == null || actionIntent.isBlank()) throw new IllegalArgumentException("action intent must not be blank");
        scope = List.copyOf(Objects.requireNonNull(scope, "scope must not be null"));
        if (scope.isEmpty() || scope.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("scope must contain authorized documents");
        }
        activeLocators = activeLocators == null ? List.of() : List.copyOf(activeLocators);
        if (activeLocators.stream().anyMatch(locator -> locator == null || locator.isBlank())
                || new HashSet<>(activeLocators).size() != activeLocators.size()) {
            throw new IllegalArgumentException("active locators must be nonblank and unique");
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
