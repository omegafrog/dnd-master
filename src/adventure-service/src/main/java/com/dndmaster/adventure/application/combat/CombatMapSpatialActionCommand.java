package com.dndmaster.adventure.application.combat;

import java.util.Objects;
import java.util.UUID;

public record CombatMapSpatialActionCommand(UUID mapId, UUID ownerPlayerId, UUID tokenId,
        CombatMapPreviewPosition cell, long expectedVersion, UUID commandId) {
    public CombatMapSpatialActionCommand {
        Objects.requireNonNull(mapId);
        Objects.requireNonNull(ownerPlayerId);
        Objects.requireNonNull(tokenId);
        Objects.requireNonNull(cell);
        Objects.requireNonNull(commandId);
        if (expectedVersion < 0) throw new IllegalArgumentException("expected map version must be non-negative");
    }
}
