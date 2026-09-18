package com.dndmaster.adventure.application.combat;

import java.util.Objects;
import java.util.UUID;

public record CombatMapSpatialTurnCommand(UUID mapId, UUID ownerPlayerId, long expectedVersion, UUID commandId) {
    public CombatMapSpatialTurnCommand {
        Objects.requireNonNull(mapId);
        Objects.requireNonNull(ownerPlayerId);
        Objects.requireNonNull(commandId);
        if (expectedVersion < 0) throw new IllegalArgumentException("expected map version must be non-negative");
    }
}
