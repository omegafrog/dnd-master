package com.dndmaster.adventure.application.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dndmaster.adventure.domain.runtime.ExplorationCycle;
import com.dndmaster.adventure.domain.runtime.ExplorationGroup;
import com.dndmaster.adventure.domain.runtime.ExplorationState;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith(OutputCaptureExtension.class)
class PendingEnvironmentEncounterContextTest {
    @Test
    void pending_encounter_narration_reaches_only_the_next_state_advancing_gm_context() {
        UUID groupId = UUID.randomUUID();
        UUID hiddenEnemyTokenId = UUID.randomUUID();
        var member = new ExplorationGroup.Member(UUID.randomUUID(), ExplorationGroup.Formation.FRONT, 30);
        var group = new ExplorationGroup(groupId, UUID.randomUUID(), List.of(member));
        var cycle = ExplorationCycle.start(UUID.randomUUID(), Map.of(groupId, 12)).finishGroupTurn(groupId, false)
                .withEnvironmentProposal(new ExplorationCycle.EnvironmentProposal("복도 끝에서 갑옷 소리가 들립니다.",
                        List.of(new ExplorationCycle.EnemyMove(hiddenEnemyTokenId,
                                List.of(new ExplorationCycle.Position(3, 4)))), 9, true, null));
        var state = new ExplorationState(List.of(group), cycle);

        List<String> normalTurn = RuntimeTurnApplicationService.withPendingEnvironmentEncounterContext(
                state, List.of("PLAYER: 소리를 확인한다"), UUID.randomUUID(), true);
        List<String> metaQuestion = RuntimeTurnApplicationService.withPendingEnvironmentEncounterContext(
                state, List.of("PLAYER: 지난 판정 규칙은?"), UUID.randomUUID(), false);

        assertEquals(2, normalTurn.size());
        assertTrue(normalTurn.getLast().contains("복도 끝에서 갑옷 소리가 들립니다."));
        assertTrue(normalTurn.getLast().contains("전투 없이 해결할지"));
        assertFalse(normalTurn.getLast().contains(hiddenEnemyTokenId.toString()));
        assertEquals(List.of("PLAYER: 지난 판정 규칙은?"), metaQuestion);
    }

    @Test
    void diagnostics_identify_context_branch_without_logging_player_visible_text(CapturedOutput output) {
        String previous = System.getProperty("ADVENTURE_RUNTIME_DIAGNOSTICS_ENABLED");
        System.setProperty("ADVENTURE_RUNTIME_DIAGNOSTICS_ENABLED", "true");
        UUID groupId = UUID.randomUUID();
        var group = new ExplorationGroup(groupId, UUID.randomUUID(), List.of(new ExplorationGroup.Member(
                UUID.randomUUID(), ExplorationGroup.Formation.FRONT, 30)));
        var cycle = ExplorationCycle.start(UUID.randomUUID(), Map.of(groupId, 12)).finishGroupTurn(groupId, false)
                .withEnvironmentProposal(new ExplorationCycle.EnvironmentProposal("비공개로 남길 서술", List.of(), 9, true, null));
        try {
            RuntimeTurnApplicationService.withPendingEnvironmentEncounterContext(new ExplorationState(List.of(group), cycle),
                    List.of(), UUID.randomUUID(), true);
        } finally {
            if (previous == null) System.clearProperty("ADVENTURE_RUNTIME_DIAGNOSTICS_ENABLED");
            else System.setProperty("ADVENTURE_RUNTIME_DIAGNOSTICS_ENABLED", previous);
        }
        assertTrue(output.getAll().contains("pending_environment_encounter_context"));
        assertTrue(output.getAll().contains("hiddenMapDetailsIncluded=false"));
        assertFalse(output.getAll().contains("비공개로 남길 서술"));
    }
}
