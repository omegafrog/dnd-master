package com.dndmaster.ruleknowledge.application.search;

import com.dndmaster.ruleknowledge.domain.rulebook.OwnerPlayerId;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Input for a single scoped dense candidate search. */
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
        List<UUID> excludedChunkIds,
        Double minimumDenseSimilarity) {
    public EvidenceSearchRequest(OwnerPlayerId ownerPlayerId, UUID sessionId, UUID scenarioPackageId, String stageKey,
            String actionIntent, List<AuthorizedDocumentScope> scope, List<String> activeLocators, String query,
            int denseLimit) {
        this(ownerPlayerId, sessionId, scenarioPackageId, stageKey, actionIntent, scope, activeLocators, query,
                denseLimit, List.of(), null);
    }
    public EvidenceSearchRequest(OwnerPlayerId ownerPlayerId, UUID sessionId, UUID scenarioPackageId, String stageKey,
            String actionIntent, List<AuthorizedDocumentScope> scope, List<String> activeLocators, String query,
            int denseLimit, List<UUID> excludedChunkIds) {
        this(ownerPlayerId, sessionId, scenarioPackageId, stageKey, actionIntent, scope, activeLocators, query,
                denseLimit, excludedChunkIds, null);
    }
    public EvidenceSearchRequest {
        Objects.requireNonNull(ownerPlayerId, "owner player id must not be null");
        if ((sessionId == null) != (scenarioPackageId == null)) {
            throw new IllegalArgumentException("session id and scenario package id must be supplied together");
        }
        if (stageKey == null || stageKey.isBlank()) throw new IllegalArgumentException("stage key must not be blank");
        if (actionIntent == null || actionIntent.isBlank()) throw new IllegalArgumentException("action intent must not be blank");
        scope = List.copyOf(Objects.requireNonNull(scope, "scope must not be null"));
        if (scope.isEmpty() || scope.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("scope must contain authorized documents");
        }
        activeLocators = activeLocators == null ? List.of() : List.copyOf(activeLocators);
        excludedChunkIds = excludedChunkIds == null ? List.of() : List.copyOf(excludedChunkIds);
        if (excludedChunkIds.stream().anyMatch(Objects::isNull) || excludedChunkIds.size() > 500
                || new HashSet<>(excludedChunkIds).size() != excludedChunkIds.size())
            throw new IllegalArgumentException("excluded chunk IDs must be unique and bounded");
        if (activeLocators.stream().anyMatch(locator -> locator == null || locator.isBlank())
                || new HashSet<>(activeLocators).size() != activeLocators.size()) {
            throw new IllegalArgumentException("active locators must be nonblank and unique");
        }
        if (query == null || query.isBlank()) throw new IllegalArgumentException("query must not be blank");
        validateLimit(denseLimit, "dense limit");
        if (minimumDenseSimilarity != null && (!Double.isFinite(minimumDenseSimilarity)
                || minimumDenseSimilarity < -1 || minimumDenseSimilarity > 1)) {
            throw new IllegalArgumentException("minimum dense similarity must be finite and between -1 and 1");
        }
    }

    private static void validateLimit(int value, String name) {
        if (value < 1 || value > 64) {
            throw new IllegalArgumentException(name + " must be between 1 and 64");
        }
    }
}
