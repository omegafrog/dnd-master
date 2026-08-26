package com.dndmaster.adventure.application.runtime;

import com.dndmaster.adventure.domain.scenario.MapDefinition;
import com.dndmaster.adventure.domain.adventure.TacticalScenePlan;
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
    default UUID prepare(UUID adventureId, UUID ownerPlayerId, UUID ruleSetId, MapDefinition definition, TacticalScenePlan scene,
            int playerSpawnX, int playerSpawnY) {
        return prepare(adventureId, ownerPlayerId, ruleSetId, definition, playerSpawnX, playerSpawnY);
    }
}
