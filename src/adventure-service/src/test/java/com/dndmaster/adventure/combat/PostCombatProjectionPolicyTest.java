package com.dndmaster.adventure.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.dndmaster.adventure.domain.combat.CombatEndProposal;
import com.dndmaster.adventure.domain.combat.CombatEvent;
import com.dndmaster.adventure.domain.combat.PostCombatProjectionPolicy;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PostCombatProjectionPolicyTest {
    @Test
    void projects_final_summary_without_detailed_combat_replay() {
        UUID adventureId = UUID.randomUUID();
        UUID encounterId = UUID.randomUUID();
        var proposal = CombatEndProposal.gm(adventureId, encounterId,
                CombatEndProposal.Reason.NEGOTIATED, "적대 상태가 해소되었습니다.");
        var projection = PostCombatProjectionPolicy.summary(proposal, List.of(
                new CombatEvent(encounterId, 1, "ACTION_RESOLVED", "{\"damage\":12}"),
                new CombatEvent(encounterId, 2, "COMBAT_ENDED", "{\"summary\":\"적대 상태가 해소되었습니다.\"}")));

        assertEquals("적대 상태가 해소되었습니다.", projection.summary());
        assertFalse(projection.detailedReplayAvailable());
        assertEquals(List.of(), projection.replay());
    }

    @Test
    void restores_only_summary_from_the_persisted_terminal_event() {
        UUID adventureId = UUID.randomUUID();
        UUID encounterId = UUID.randomUUID();
        var proposal = CombatEndProposal.gm(adventureId, encounterId,
                CombatEndProposal.Reason.OBJECTIVE_ACHIEVED, "목표를 달성했습니다.");

        var projection = PostCombatProjectionPolicy.fromEndedEvent(
                PostCombatProjectionPolicy.endedEvent(proposal, 9));

        assertEquals(adventureId, projection.adventureId());
        assertEquals(encounterId, projection.encounterId());
        assertEquals("OBJECTIVE_ACHIEVED", projection.reason());
        assertEquals("목표를 달성했습니다.", projection.summary());
        assertFalse(projection.detailedReplayAvailable());
        assertEquals(List.of(), projection.replay());
    }
}
