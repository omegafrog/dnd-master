package com.dndmaster.adventure.application.runtime;

import com.dndmaster.adventure.domain.scenario.MapDefinition;
import java.util.UUID;

/** Cross-context seam for materializing a compiled map in Combat Map. */
public interface TacticalMapPreparationPort {
    UUID prepare(UUID adventureId, UUID ownerPlayerId, MapDefinition definition);
    default UUID prepare(UUID adventureId, UUID ownerPlayerId, UUID ruleSetId, MapDefinition definition) {
        return prepare(adventureId, ownerPlayerId, definition);
    }
    default UUID prepare(UUID adventureId, UUID ownerPlayerId, UUID ruleSetId, MapDefinition definition, int playerSpawnX, int playerSpawnY) {
        return prepare(adventureId, ownerPlayerId, ruleSetId, definition);
    }
}
