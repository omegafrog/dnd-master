package com.dndmaster.adventure.application.runtime;

import java.util.Optional;
import java.util.UUID;

/** Durable lookup for the map currently owned by an adventure. */
public interface ActiveTacticalMapPort {
    Optional<UUID> findActiveMap(UUID adventureId, int stagePosition, UUID ownerPlayerId);
    default void bindActiveMap(UUID adventureId, int stagePosition, UUID ownerPlayerId, UUID combatMapId) {
        throw new UnsupportedOperationException("active map binding is not configured");
    }
}
