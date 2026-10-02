package com.dndmaster.adventure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dndmaster.adventure.application.runtime.*;
import com.dndmaster.adventure.domain.adventure.AdventureContext;
import com.dndmaster.adventure.domain.adventure.AdventureId;
import com.dndmaster.adventure.domain.adventure.CharacterSheetId;
import com.dndmaster.adventure.domain.runtime.CompletionProposal;
import com.dndmaster.adventure.domain.runtime.CurrentSituation;
import com.dndmaster.adventure.domain.runtime.DisclosureState;
import com.dndmaster.adventure.domain.runtime.GameStateDelta;
import com.dndmaster.adventure.domain.runtime.PendingRuntimeState;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RuntimeCheckProposalTest {
    @Test
    void accepts_rulebook_dice_other_than_a_d20_and_keeps_modifier_separate() {
        RuntimeCheckProposal proposal = new RuntimeCheckProposal(true, "약초를 조사합니다.", "자연",
                UUID.randomUUID(), RuntimeCheckProposal.RollMethod.PLAYER, "1d4+2", 1, 5,
                List.of("RULEBOOK:rules:2:page=18"), "성공", "실패");

        assertEquals("1d4", proposal.diceExpression());
        assertEquals(3, proposal.modifier());
        assertTrue(TypedCheckRule.DiceExpression.parse(proposal.diceExpression(), 0).acceptsRollTotal(4));
        assertFalse(TypedCheckRule.DiceExpression.parse(proposal.diceExpression(), 0).acceptsRollTotal(5));
    }

    @Test
    void saves_a_turn_check_before_roll_and_resolves_it_without_matching_player_words_again() {
        UUID characterId = UUID.randomUUID();
        String evidenceKey = "RULEBOOK:rules:2:page=18";
        RuntimeCheckProposal proposal = new RuntimeCheckProposal(true, "소리의 방향을 확인해야 합니다.", "지각",
                characterId, RuntimeCheckProposal.RollMethod.PLAYER, "1d20", 2, 12, List.of(evidenceKey),
                "왼쪽 문 뒤에서 움직임을 알아챕니다.", "움직임의 방향을 특정하지 못합니다.");
        RuntimePlan plan = new RuntimePlan("복도", "", "지각 판정", "굴림 결과를 기다립니다.", null,
                List.of(), List.of()).withCheckProposal(proposal);
        UUID turnId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID packageId = UUID.randomUUID();
        RuntimeTurn requested = new RuntimeTurn(turnId, UUID.randomUUID(), AdventureId.generate(), sessionId,
                packageId, 1, "소리를 확인한다", new EvidencePack(List.of(), List.of(), List.of()), plan,
                null, new AdventureContext("복도", "", "", ""), List.of(), 3, List.of(), List.of(),
                false, true, RuntimeTurnOrigin.PLAYER, false, new CharacterSheetId(characterId), 0, 3L,
                false, false, RuntimeTurnLifecycle.REQUESTED, null);
        PendingRuntimeState pendingState = new PendingRuntimeState(GameStateDelta.empty(), DisclosureState.empty(),
                CurrentSituation.initial("복도의 소리를 확인한다"), List.of());

        RuntimeTurn pending = requested.pendingPlayerRoll(pendingState, CompletionProposal.continueAdventure());

        assertEquals(RuntimeTurnLifecycle.PENDING_ROLL, pending.lifecycle());
        assertEquals(3, pending.version());
        assertFalse(pending.committed());
        assertEquals(proposal, pending.plan().checkProposal());
        assertEquals("소리를 확인한다", pending.action());

        RuntimeTurn resolved = pending.resolvePlayerRoll(10, 12, true);

        assertEquals(RuntimeTurnLifecycle.RESOLVED_UNCOMMITTED, resolved.lifecycle());
        assertTrue(resolved.plan().judgment().contains("성공"));
        assertEquals("왼쪽 문 뒤에서 움직임을 알아챕니다.", resolved.plan().narration());
        assertEquals(List.of("PLAYER_ROLL=10", "CHECK_TOTAL=12", "CHECK_SUCCEEDED"),
                resolved.resolvedArtifact().outcomes());
        assertEquals(pendingState, resolved.pendingState());
    }

    @Test
    void rejects_a_check_without_grounded_evidence() {
        assertThrows(IllegalArgumentException.class, () -> new RuntimeCheckProposal(true, "소리를 살핍니다.", "지각",
                UUID.randomUUID(), RuntimeCheckProposal.RollMethod.PLAYER, "1d20", 1, 12, List.of(),
                "성공 결과", "실패 결과"));
    }
}
