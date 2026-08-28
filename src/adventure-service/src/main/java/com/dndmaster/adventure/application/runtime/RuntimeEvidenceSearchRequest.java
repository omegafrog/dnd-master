package com.dndmaster.adventure.application.runtime;

import com.dndmaster.adventure.domain.adventure.ActiveSourceContext;
import com.dndmaster.adventure.domain.adventure.AdventureId;
import com.dndmaster.adventure.domain.adventure.OwnerPlayerId;
import com.dndmaster.adventure.domain.adventure.SessionId;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.Map;

public record RuntimeEvidenceSearchRequest(
        AdventureId adventureId,
        OwnerPlayerId ownerPlayerId,
        SessionId sessionId,
        UUID scenarioPackageId,
        List<UUID> knowledgeDocumentIds,
        ActiveSourceContext activeSourceContext,
        String action,
        RuntimeEvidenceType evidenceType,
        int limit,
        Map<UUID, Long> extractionVersions,
        String stageKey,
        String actionIntent) {
    public RuntimeEvidenceSearchRequest(AdventureId adventureId, OwnerPlayerId ownerPlayerId, SessionId sessionId,
                                         UUID scenarioPackageId, List<UUID> knowledgeDocumentIds,
                                         ActiveSourceContext activeSourceContext, String action,
                                         RuntimeEvidenceType evidenceType, int limit) {
        this(adventureId, ownerPlayerId, sessionId, scenarioPackageId, knowledgeDocumentIds,
                activeSourceContext, action, evidenceType, limit, Map.of(), "current", "MIXED");
    }

    public RuntimeEvidenceSearchRequest(AdventureId adventureId, OwnerPlayerId ownerPlayerId, SessionId sessionId,
                                         UUID scenarioPackageId, List<UUID> knowledgeDocumentIds,
                                         ActiveSourceContext activeSourceContext, String action,
                                         RuntimeEvidenceType evidenceType, int limit,
                                         Map<UUID, Long> extractionVersions) {
        this(adventureId, ownerPlayerId, sessionId, scenarioPackageId, knowledgeDocumentIds,
                activeSourceContext, action, evidenceType, limit, extractionVersions, "current", "MIXED");
    }

    public RuntimeEvidenceSearchRequest {
        adventureId = Objects.requireNonNull(adventureId, "adventure id must not be null");
        ownerPlayerId = Objects.requireNonNull(ownerPlayerId, "owner player id must not be null");
        sessionId = Objects.requireNonNull(sessionId, "session id must not be null");
        scenarioPackageId = Objects.requireNonNull(scenarioPackageId, "scenario package id must not be null");
        knowledgeDocumentIds = List.copyOf(Objects.requireNonNull(knowledgeDocumentIds, "knowledge document ids must not be null"));
        extractionVersions = Map.copyOf(Objects.requireNonNull(extractionVersions, "extraction versions must not be null"));
        action = required(action, "action");
        evidenceType = Objects.requireNonNull(evidenceType, "evidence type must not be null");
        stageKey = required(stageKey, "stage key");
        actionIntent = required(actionIntent, "action intent");
        if (limit <= 0) throw new IllegalArgumentException("limit must be positive");
    }

    public RuntimeEvidenceSearchRequest forType(RuntimeEvidenceType type, int requestedLimit) {
        return new RuntimeEvidenceSearchRequest(adventureId, ownerPlayerId, sessionId, scenarioPackageId,
                knowledgeDocumentIds, activeSourceContext, action, type, requestedLimit, extractionVersions,
                stageKey, actionIntent);
    }

    public RuntimeEvidenceSearchRequest withDocumentIds(List<UUID> documentIds, RuntimeEvidenceType type, int requestedLimit) {
        return new RuntimeEvidenceSearchRequest(adventureId, ownerPlayerId, sessionId, scenarioPackageId,
                documentIds, activeSourceContext, action, type, requestedLimit, extractionVersions,
                stageKey, actionIntent);
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value.trim();
    }
}
