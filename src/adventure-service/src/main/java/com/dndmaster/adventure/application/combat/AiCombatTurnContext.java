package com.dndmaster.adventure.application.combat;

import com.dndmaster.adventure.domain.combat.CombatEncounter;
import com.dndmaster.adventure.domain.combat.CombatParticipant;
import java.util.Objects;

public record AiCombatTurnContext(CombatEncounter encounter, CombatParticipant actor,
                                  AiTacticalInstructionContext tacticalInstruction) {
    public AiCombatTurnContext {
        Objects.requireNonNull(encounter, "encounter must not be null");
        Objects.requireNonNull(actor, "actor must not be null");
        Objects.requireNonNull(tacticalInstruction, "tactical instruction must not be null");
    }
}
