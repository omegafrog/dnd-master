package com.dndmaster.adventure.combat;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dndmaster.adventure.domain.combat.CombatEncounter;
import com.dndmaster.adventure.domain.combat.CombatParticipant;
import com.dndmaster.adventure.domain.combat.CombatRulesEngine;
import com.dndmaster.adventure.domain.combat.CombatEffectProposal;
import com.dndmaster.adventure.domain.combat.FreeFormActionPlan;
import com.dndmaster.adventure.domain.combat.TurnResourceCost;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AiProposalValidationPolicyTest {
    @Test
    void rejects_a_proposal_target_that_is_not_a_combat_participant() {
        UUID actorId = UUID.randomUUID();
        CombatEncounter encounter = encounter(actorId);
        FreeFormActionPlan proposal = new FreeFormActionPlan(actorId, UUID.randomUUID(),
                TurnResourceCost.actionOnly(), true, 10, 5,
                CombatEffectProposal.damage(-4), "hit", "A hit.");

        var evaluation = new CombatRulesEngine().validateFreeFormProposal(encounter, proposal);

        assertFalse(evaluation.accepted());
        assertTrue(evaluation.violations().contains("TARGET_NOT_IN_ENCOUNTER"));
    }

    @Test
    void rejects_a_proposal_effect_that_does_not_have_a_target_without_mutating_the_encounter() {
        UUID actorId = UUID.randomUUID();
        CombatEncounter encounter = encounter(actorId);
        FreeFormActionPlan proposal = new FreeFormActionPlan(actorId, null,
                TurnResourceCost.actionOnly(), true, null, null,
                CombatEffectProposal.damage(-4), "it fails", "The throw misses.");

        var evaluation = new CombatRulesEngine().validateFreeFormProposal(encounter, proposal);

        assertFalse(evaluation.accepted());
        assertTrue(evaluation.violations().contains("TARGET_REQUIRED_FOR_EFFECT"));
        assertTrue(evaluation.violations().contains("ROLL_REQUIRED_FOR_ATTACK"));
        assertTrue(encounter.currentParticipant().resources().actionAvailable());
    }

    private static CombatEncounter encounter(UUID actorId) {
        return new CombatEncounter(UUID.randomUUID(), UUID.randomUUID(), CombatEncounter.Status.ACTIVE,
                1, actorId, List.of(
                        new CombatParticipant(actorId, "Hero", CombatParticipant.Controller.PLAYER, 15, "healthy"),
                        new CombatParticipant(UUID.randomUUID(), "Goblin", CombatParticipant.Controller.AI, 10, null)),
                1, 0);
    }
}
