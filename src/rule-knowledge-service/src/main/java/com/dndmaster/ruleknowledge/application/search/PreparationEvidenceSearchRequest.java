package com.dndmaster.ruleknowledge.application.search;

import com.dndmaster.ruleknowledge.domain.rulebook.DocumentType;
import com.dndmaster.ruleknowledge.domain.rulebook.OwnerPlayerId;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Pre-session input. It deliberately has no Adventure Session or Scenario Package identifier. */
public record PreparationEvidenceSearchRequest(
        OwnerPlayerId ownerPlayerId,
        UUID scenarioSourceBundleId,
        List<AuthorizedDocumentScope> scope,
        List<String> activeLocators,
        String query,
        int denseLimit,
        int bm25Limit) {
    public PreparationEvidenceSearchRequest {
        Objects.requireNonNull(ownerPlayerId, "owner player id must not be null");
        Objects.requireNonNull(scenarioSourceBundleId, "scenario source bundle id must not be null");
        scope = List.copyOf(Objects.requireNonNull(scope, "scope must not be null"));
        if (scope.isEmpty() || scope.stream().anyMatch(item -> item.documentType() != DocumentType.STORYBOOK
                && item.documentType() != DocumentType.RULEBOOK)) {
            throw new IllegalArgumentException("preparation scope must contain only storybook or rulebook documents");
        }
    }

    public EvidenceSearchRequest asSharedSearchRequest() {
        return new EvidenceSearchRequest(ownerPlayerId, null, null, "scenario-preparation", "SCENARIO_PREPARATION",
                scope, activeLocators, query, denseLimit, bm25Limit);
    }
}
