package com.dndmaster.adventure.application.combat;

import java.util.Objects;
import java.util.UUID;

public record CombatMapGameTimeCommand(UUID adventureId, UUID mapId, UUID ownerPlayerId,
        long ruleTurn, long expectedVersion, UUID commandId) {
    public CombatMapGameTimeCommand {
        Objects.requireNonNull(adventureId);
        Objects.requireNonNull(mapId);
        Objects.requireNonNull(ownerPlayerId);
        Objects.requireNonNull(commandId);
        if (ruleTurn < 0 || expectedVersion < 0) throw new IllegalArgumentException("game time values must be non-negative");
    }
}
