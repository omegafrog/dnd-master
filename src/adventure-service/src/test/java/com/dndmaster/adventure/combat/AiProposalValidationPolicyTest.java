package com.dndmaster.adventure.combat;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dndmaster.adventure.domain.combat.CombatEncounter;
import com.dndmaster.adventure.domain.combat.CombatParticipant;
import com.dndmaster.adventure.domain.combat.CombatRulesEngine;
import com.dndmaster.adventure.domain.combat.CombatEffectProposal;
import com.dndmaster.adventure.domain.combat.FreeFormActionPlan;
import com.dndmaster.adventure.domain.combat.TurnResourceCost;
import com.dndmaster.adventure.domain.combat.CombatEnemyStatBlock;
import com.dndmaster.adventure.domain.combat.CombatStatBlockSource;
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

    @Test
    void uses_the_materialized_enemy_armor_class_when_the_gm_does_not_repeat_it() {
        UUID actorId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        CombatEnemyStatBlock stats = new CombatEnemyStatBlock(12, 7, 4, "1d6 + 2",
                new CombatStatBlockSource(UUID.randomUUID(), 1, "page-135"));
        CombatEncounter encounter = new CombatEncounter(UUID.randomUUID(), UUID.randomUUID(), CombatEncounter.Status.ACTIVE,
                1, actorId, List.of(new CombatParticipant(actorId, "Hero", CombatParticipant.Controller.PLAYER, 15, "healthy"),
                        new CombatParticipant(targetId, "Giant Rat", CombatParticipant.Controller.AI, 10, "enemy",
                                com.dndmaster.adventure.domain.combat.TurnResources.initial(), stats)), 1, 0);

        var evaluation = new CombatRulesEngine().validateFreeFormProposal(encounter,
                new FreeFormActionPlan(actorId, targetId, TurnResourceCost.actionOnly(), true,
                        null, 5, CombatEffectProposal.damage(-4), "hit", "The attack lands."));

        assertTrue(evaluation.accepted());
    }

    private static CombatEncounter encounter(UUID actorId) {
        return new CombatEncounter(UUID.randomUUID(), UUID.randomUUID(), CombatEncounter.Status.ACTIVE,
                1, actorId, List.of(
                        new CombatParticipant(actorId, "Hero", CombatParticipant.Controller.PLAYER, 15, "healthy"),
                        new CombatParticipant(UUID.randomUUID(), "Goblin", CombatParticipant.Controller.AI, 10, null)),
                1, 0);
    }
}
