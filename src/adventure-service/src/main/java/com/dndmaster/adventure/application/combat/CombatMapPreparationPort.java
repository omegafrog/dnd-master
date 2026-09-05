package com.dndmaster.adventure.application.combat;

import com.dndmaster.adventure.domain.adventure.AdventureId;
import com.dndmaster.adventure.domain.adventure.RuleSetId;
import com.dndmaster.adventure.domain.scenario.MapDefinition;
import java.util.UUID;

/** Prepares and activates the map for one entered adventure stage. */
public interface CombatMapPreparationPort {
    UUID prepareInitial(AdventureId adventureId, UUID ownerPlayerId, RuleSetId ruleSetId,
            MapDefinition mapDefinition, int stagePosition);
}
