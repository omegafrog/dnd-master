package com.dndmaster.adventure.application.runtime;

import com.dndmaster.adventure.domain.adventure.ActiveSourceContext;
import com.dndmaster.adventure.domain.adventure.AdventureId;
import com.dndmaster.adventure.domain.adventure.OwnerPlayerId;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record RuntimeEvidenceSearchRequest(
        AdventureId adventureId,
        OwnerPlayerId ownerPlayerId,
        UUID scenarioPackageId,
        List<UUID> rulebookIds,
        ActiveSourceContext activeSourceContext,
        String action,
        RuntimeEvidenceType evidenceType,
        int limit) {
    public RuntimeEvidenceSearchRequest {
        adventureId = Objects.requireNonNull(adventureId, "adventure id must not be null");
        ownerPlayerId = Objects.requireNonNull(ownerPlayerId, "owner player id must not be null");
        scenarioPackageId = Objects.requireNonNull(scenarioPackageId, "scenario package id must not be null");
        rulebookIds = List.copyOf(Objects.requireNonNull(rulebookIds, "rulebook ids must not be null"));
        action = required(action, "action");
        evidenceType = Objects.requireNonNull(evidenceType, "evidence type must not be null");
        if (limit <= 0) throw new IllegalArgumentException("limit must be positive");
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value.trim();
    }
}
