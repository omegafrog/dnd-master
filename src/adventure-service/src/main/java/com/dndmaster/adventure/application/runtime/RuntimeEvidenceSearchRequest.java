package com.dndmaster.adventure.application.runtime;

import com.dndmaster.adventure.domain.adventure.ActiveSourceContext;
import com.dndmaster.adventure.domain.adventure.AdventureId;
import com.dndmaster.adventure.domain.adventure.OwnerPlayerId;
import com.dndmaster.adventure.domain.adventure.SessionId;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record RuntimeEvidenceSearchRequest(
        AdventureId adventureId,
        OwnerPlayerId ownerPlayerId,
        SessionId sessionId,
        UUID scenarioPackageId,
        List<UUID> knowledgeDocumentIds,
        Map<UUID, Long> extractionVersions,
        ActiveSourceContext activeSourceContext,
        String action,
        RuntimeEvidenceType evidenceType,
        int limit) {
    public RuntimeEvidenceSearchRequest {
        adventureId = Objects.requireNonNull(adventureId, "adventure id must not be null");
        ownerPlayerId = Objects.requireNonNull(ownerPlayerId, "owner player id must not be null");
        sessionId = Objects.requireNonNull(sessionId, "session id must not be null");
        scenarioPackageId = Objects.requireNonNull(scenarioPackageId, "scenario package id must not be null");
        knowledgeDocumentIds = List.copyOf(Objects.requireNonNull(knowledgeDocumentIds, "knowledge document ids must not be null"));
        extractionVersions = Map.copyOf(Objects.requireNonNull(extractionVersions, "extraction versions must not be null"));
        for (Map.Entry<UUID, Long> entry : extractionVersions.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null || entry.getValue() <= 0) {
                throw new IllegalArgumentException("extraction versions must be positive");
            }
        }
        action = required(action, "action");
        evidenceType = Objects.requireNonNull(evidenceType, "evidence type must not be null");
        if (limit <= 0) throw new IllegalArgumentException("limit must be positive");
    }

    public RuntimeEvidenceSearchRequest(
            AdventureId adventureId, OwnerPlayerId ownerPlayerId, SessionId sessionId, UUID scenarioPackageId,
            List<UUID> knowledgeDocumentIds, ActiveSourceContext activeSourceContext, String action,
            RuntimeEvidenceType evidenceType, int limit) {
        this(adventureId, ownerPlayerId, sessionId, scenarioPackageId, knowledgeDocumentIds,
                activeSourceContext == null ? Map.of() : Map.of(activeSourceContext.knowledgeDocumentId().value(), activeSourceContext.extractionVersion()),
                activeSourceContext, action, evidenceType, limit);
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value.trim();
    }
}
