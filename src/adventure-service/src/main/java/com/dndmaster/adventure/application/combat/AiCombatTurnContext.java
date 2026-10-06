package com.dndmaster.adventure.application.combat;

import com.dndmaster.adventure.domain.combat.CombatEncounter;
import com.dndmaster.adventure.domain.combat.CombatParticipant;
import java.util.Objects;
import java.util.UUID;

public record AiCombatTurnContext(CombatEncounter encounter, CombatParticipant actor,
                                  AiTacticalInstructionContext tacticalInstruction,
                                  String currentSituation, String characterSheetJson,
                                  EnemyCharacterSheet enemyCharacterSheet, UUID ownerPlayerId,
                                  com.dndmaster.adventure.application.runtime.GmProviderSelection providerSelection) {
    public AiCombatTurnContext(CombatEncounter encounter, CombatParticipant actor,
                               AiTacticalInstructionContext tacticalInstruction) {
        this(encounter, actor, tacticalInstruction, null, null, null, null, null);
    }

    public AiCombatTurnContext {
        Objects.requireNonNull(encounter, "encounter must not be null");
        Objects.requireNonNull(actor, "actor must not be null");
        Objects.requireNonNull(tacticalInstruction, "tactical instruction must not be null");
        if (actor.enemyKind() != null && enemyCharacterSheet != null
                && !actor.enemyKind().equals(enemyCharacterSheet.identity().enemyKind())) {
            throw new IllegalArgumentException("enemy sheet does not match current actor");
        }
    }
}
