package com.dndmaster.adventure.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.dndmaster.adventure.application.combat.AiCombatTurnContext;
import com.dndmaster.adventure.application.combat.AiTacticalInstructionContext;
import com.dndmaster.adventure.application.combat.AiTacticalInstructionPolicy;
import com.dndmaster.adventure.domain.combat.CombatParticipant;
import com.dndmaster.adventure.domain.combat.CombatStartPolicy;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AiTacticalInstructionPolicyTest {
    @Test
    void passes_typed_p1_instruction_context_to_ai_turn_planning() {
        UUID ai = UUID.randomUUID();
        var encounter = CombatStartPolicy.startFromCommittedGmTurn(true, UUID.randomUUID(), List.of(
                new CombatParticipant(ai, "Companion", CombatParticipant.Controller.AI, 20, null)));

        AiCombatTurnContext context = AiTacticalInstructionPolicy.contextFor(encounter, ai,
                "Protect the healer", List.of("stay near the party"));

        assertEquals("Protect the healer", context.tacticalInstruction().instruction());
        assertEquals(List.of("stay near the party"), context.tacticalInstruction().constraints());
        assertEquals(ai, context.actor().participantId());
    }
}
