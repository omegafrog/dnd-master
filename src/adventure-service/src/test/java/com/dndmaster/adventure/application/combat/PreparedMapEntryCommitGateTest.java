package com.dndmaster.adventure.application.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dndmaster.adventure.application.runtime.RuntimePlan;
import com.dndmaster.adventure.application.runtime.RuntimeTurn;
import com.dndmaster.adventure.domain.adventure.Adventure;
import com.dndmaster.adventure.domain.adventure.AdventureContext;
import com.dndmaster.adventure.domain.adventure.AdventureId;
import com.dndmaster.adventure.domain.adventure.OwnerPlayerId;
import com.dndmaster.adventure.domain.adventure.RuleSetId;
import com.dndmaster.adventure.domain.runtime.CurrentSituation;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class PreparedMapEntryCommitGateTest {
    @Test
    void activates_a_prepared_map_when_the_scene_changes_even_if_gm_omits_the_entry_flag() {
        Adventure adventure = adventureAt("beer-cellar");
        RuntimeTurn turn = turnFrom("opening-situation", false, false);
        CombatMapViewPort mapView = mock(CombatMapViewPort.class);
        CombatMapPreparationPort mapPreparation = mock(CombatMapPreparationPort.class);
        when(mapView.hasPreparedMap(any(), any())).thenReturn(true);

        new PreparedMapEntryCommitGate(mapView, mapPreparation).beforeCommit(adventure, turn);

        ArgumentCaptor<CombatMapPreparationPort.ActivationContext> context = ArgumentCaptor.forClass(CombatMapPreparationPort.ActivationContext.class);
        verify(mapPreparation).activatePrepared(any(), any(), any(), eq(1), context.capture());
        assertEquals("beer-cellar", context.getValue().currentScene());
        assertEquals("beer-cellar", context.getValue().location());
    }

    @Test
    void does_not_activate_a_map_for_a_scene_change_when_no_prepared_map_exists() {
        Adventure adventure = adventureAt("beer-cellar");
        RuntimeTurn turn = turnFrom("opening-situation", false, false);
        CombatMapViewPort mapView = mock(CombatMapViewPort.class);
        CombatMapPreparationPort mapPreparation = mock(CombatMapPreparationPort.class);
        when(mapView.hasPreparedMap(any(), any())).thenReturn(false);

        new PreparedMapEntryCommitGate(mapView, mapPreparation).beforeCommit(adventure, turn);

        verify(mapPreparation, never()).activatePrepared(any(), any(), any(), any(int.class), any());
    }

    private static Adventure adventureAt(String scene) {
        Adventure adventure = mock(Adventure.class);
        when(adventure.id()).thenReturn(AdventureId.generate());
        when(adventure.ownerPlayerId()).thenReturn(new OwnerPlayerId(UUID.randomUUID()));
        when(adventure.ruleSetId()).thenReturn(new RuleSetId(UUID.randomUUID()));
        when(adventure.currentSituation()).thenReturn(new CurrentSituation(UUID.randomUUID(), 2, "opening",
                "문제", "위협", "목표", null, "첫 장면"));
        when(adventure.currentContext()).thenReturn(new AdventureContext(scene, null, "해치를 연다", "도착했다"));
        when(adventure.party()).thenReturn(List.of());
        when(adventure.turnIndex()).thenReturn(0);
        return adventure;
    }

    private static RuntimeTurn turnFrom(String previousScene, boolean mapEntry, boolean combatStart) {
        RuntimeTurn turn = mock(RuntimeTurn.class);
        RuntimePlan plan = mock(RuntimePlan.class);
        when(turn.context()).thenReturn(new AdventureContext(previousScene, null, "해치를 연다", ""));
        when(turn.plan()).thenReturn(plan);
        when(turn.action()).thenReturn("해치를 열고 내려간다");
        when(turn.narration()).thenReturn("저장고 안에 도착했다.");
        when(plan.mapEntryRequested()).thenReturn(mapEntry);
        when(plan.combatStartRequested()).thenReturn(combatStart);
        when(plan.judgment()).thenReturn("저장고에 도착했다.");
        when(plan.citedEvidence()).thenReturn(List.of());
        return turn;
    }
}
