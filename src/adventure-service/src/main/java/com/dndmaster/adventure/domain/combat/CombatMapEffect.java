package com.dndmaster.adventure.domain.combat;

import java.util.Objects;
import java.util.UUID;

/** A typed map effect proposed by AI; it is executed only through CombatMapPort. */
public record CombatMapEffect(UUID mapId, UUID tokenId, String movementPath, int movementDistance, long expectedVersion) {
    public CombatMapEffect {
        Objects.requireNonNull(mapId, "map id must not be null");
        Objects.requireNonNull(tokenId, "token id must not be null");
        if (movementPath == null || movementPath.isBlank()) throw new IllegalArgumentException("map effect path is required");
        if (movementDistance <= 0) throw new IllegalArgumentException("map effect distance must be positive");
        if (expectedVersion < 0) throw new IllegalArgumentException("map effect version must be non-negative");
        movementPath = movementPath.trim();
    }
}
