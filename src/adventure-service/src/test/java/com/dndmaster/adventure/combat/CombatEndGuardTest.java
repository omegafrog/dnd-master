package com.dndmaster.adventure.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dndmaster.adventure.domain.combat.CombatEndGuard;
import com.dndmaster.adventure.domain.combat.CombatEndProposal;
import com.dndmaster.adventure.domain.combat.CombatEncounter;
import com.dndmaster.adventure.domain.combat.CombatParticipant;
import com.dndmaster.adventure.domain.combat.CombatStartPolicy;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CombatEndGuardTest {
    @Test
    void rejects_pending_action_reaction_or_work_until_external_effects_are_committed() {
        UUID adventureId = UUID.randomUUID();
        CombatEncounter encounter = CombatStartPolicy.startFromCommittedGmTurn(true, adventureId, List.of(
                new CombatParticipant(UUID.randomUUID(), "Hero", CombatParticipant.Controller.PLAYER, 20, null)));
        var proposal = CombatEndProposal.gm(adventureId, encounter.encounterId(),
                CombatEndProposal.Reason.OBJECTIVE_ACHIEVED, "목표를 달성했습니다.");
        var guard = new CombatEndGuard();

        assertEquals("PENDING_ACTION", guard.check(encounter, proposal, true, false, true).code());
        assertEquals("PENDING_REACTION", guard.check(encounter.requestReaction(
                new com.dndmaster.adventure.domain.combat.ReactionInterrupt(
                        UUID.randomUUID(), "trigger", encounter.currentParticipantId(), UUID.randomUUID(), "dice", List.of()),
                encounter.version()), proposal, false, false, true).code());
        assertEquals("PENDING_WORK", guard.check(encounter, proposal, false, true, true).code());
        assertEquals("EXTERNAL_EFFECTS_NOT_COMMITTED", guard.check(encounter, proposal, false, false, false).code());
        assertTrue(guard.check(encounter, proposal, false, false, true).accepted());
    }
}
