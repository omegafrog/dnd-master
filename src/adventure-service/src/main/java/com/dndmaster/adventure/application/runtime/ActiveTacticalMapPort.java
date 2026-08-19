package com.dndmaster.adventure.application.runtime;

import java.util.Optional;
import java.util.UUID;

/** Durable lookup for the map currently owned by an adventure. */
public interface ActiveTacticalMapPort {
    Optional<UUID> findActiveMap(UUID adventureId, UUID ownerPlayerId);
}
