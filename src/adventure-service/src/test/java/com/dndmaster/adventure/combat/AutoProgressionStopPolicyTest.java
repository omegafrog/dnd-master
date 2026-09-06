package com.dndmaster.adventure.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.dndmaster.adventure.application.combat.AutoProgressionStopPolicy;
import com.dndmaster.adventure.domain.combat.CombatEncounter;
import com.dndmaster.adventure.domain.combat.CombatParticipant;
import com.dndmaster.adventure.domain.combat.CombatStartPolicy;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AutoProgressionStopPolicyTest {
    @Test
    void stops_at_human_reaction_ended_and_max_step_boundaries() {
        UUID human = UUID.randomUUID();
        UUID ai = UUID.randomUUID();
        CombatEncounter humanTurn = CombatStartPolicy.startFromCommittedGmTurn(true, UUID.randomUUID(), List.of(
                new CombatParticipant(human, "Hero", CombatParticipant.Controller.PLAYER, 20, null),
                new CombatParticipant(ai, "Goblin", CombatParticipant.Controller.AI, 10, null)));
        assertEquals(AutoProgressionStopPolicy.Reason.HUMAN_TURN,
                AutoProgressionStopPolicy.reason(humanTurn, 0, 10));

        CombatEncounter reactionPending = humanTurn.requestReaction(
                new com.dndmaster.adventure.domain.combat.ReactionInterrupt(
                        UUID.randomUUID(), "trigger", human, UUID.randomUUID(), "dice", List.of()),
                humanTurn.version());
        assertEquals(AutoProgressionStopPolicy.Reason.REACTION_PENDING,
                AutoProgressionStopPolicy.reason(reactionPending, 0, 10));

        CombatEncounter ended = new CombatEncounter(humanTurn.encounterId(), humanTurn.adventureId(),
                CombatEncounter.Status.ENDED, humanTurn.round(), humanTurn.currentParticipantId(),
                humanTurn.participants(), humanTurn.version(), humanTurn.eventCursor());
        assertEquals(AutoProgressionStopPolicy.Reason.ENDED,
                AutoProgressionStopPolicy.reason(ended, 0, 10));
        CombatEncounter aiTurn = CombatStartPolicy.startFromCommittedGmTurn(true, UUID.randomUUID(), List.of(
                new CombatParticipant(ai, "Goblin", CombatParticipant.Controller.AI, 20, null),
                new CombatParticipant(human, "Hero", CombatParticipant.Controller.PLAYER, 10, null)));
        assertEquals(AutoProgressionStopPolicy.Reason.MAX_STEPS,
                AutoProgressionStopPolicy.reason(aiTurn, 3, 3));
    }
}
