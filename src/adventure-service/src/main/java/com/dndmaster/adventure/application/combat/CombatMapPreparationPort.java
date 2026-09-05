package com.dndmaster.adventure.application.combat;

import com.dndmaster.adventure.domain.adventure.AdventureId;
import com.dndmaster.adventure.domain.adventure.RuleSetId;
import com.dndmaster.adventure.domain.scenario.MapDefinition;
import java.util.UUID;

/** Prepares and activates the map for one entered adventure stage. */
public interface CombatMapPreparationPort {
    UUID prepareInitial(AdventureId adventureId, UUID ownerPlayerId, RuleSetId ruleSetId,
            MapDefinition mapDefinition, int stagePosition);

    default UUID prepareInitial(AdventureId adventureId, UUID ownerPlayerId, RuleSetId ruleSetId,
            MapDefinition mapDefinition, int stagePosition, ActivationContext context) {
        return prepareInitial(adventureId, ownerPlayerId, ruleSetId, mapDefinition, stagePosition);
    }

    /** Structured runtime context crossing into Combat Map; prose stays in Adventure Runtime. */
    record ActivationContext(UUID playerTokenId, UUID situationId, long situationRevision, int turnIndex,
            String currentScene, String location, Integer spawnCandidateX, Integer spawnCandidateY, String entrySide) {
        public ActivationContext {
            if (situationRevision < 1) throw new IllegalArgumentException("situation revision must be positive");
            if (turnIndex < 0) throw new IllegalArgumentException("turn index must not be negative");
            if ((spawnCandidateX == null) != (spawnCandidateY == null)) {
                throw new IllegalArgumentException("spawn candidate coordinates must be paired");
            }
        }
    }
}
