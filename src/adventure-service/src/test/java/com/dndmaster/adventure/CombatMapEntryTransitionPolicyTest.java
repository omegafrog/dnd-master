package com.dndmaster.adventure;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.dndmaster.adventure.application.combat.CombatMapEntryTransitionPolicy;
import com.dndmaster.adventure.domain.adventure.Adventure;
import com.dndmaster.adventure.domain.adventure.AdventureContext;
import com.dndmaster.adventure.domain.runtime.CurrentSituation;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CombatMapEntryTransitionPolicyTest {
    @Test
    void activates_a_prepared_map_when_a_committed_scene_enters_the_cellar() {
        Adventure before = adventure("양조장", "양조장");
        Adventure after = adventure("맥주 저장고", "맥주 저장고");
        when(after.currentSituation()).thenReturn(new CurrentSituation(UUID.randomUUID(), 1,
                "맥주 저장고", "쥐를 찾는다", "거대 쥐", "저장고를 조사한다"));

        assertTrue(CombatMapEntryTransitionPolicy.enteredMap(before, after));
    }

    @Test
    void does_not_activate_a_map_for_an_unrelated_scene_change() {
        Adventure before = adventure("양조장", "양조장");
        Adventure after = adventure("마을 광장", "마을 광장");

        assertFalse(CombatMapEntryTransitionPolicy.enteredMap(before, after));
    }

    @Test
    void uses_the_pre_turn_snapshot_when_the_repository_reuses_the_same_aggregate_instance() {
        Adventure before = adventure("양조장", "양조장");
        Adventure after = adventure("맥주 저장고", "맥주 저장고");
        CurrentSituation previous = before.currentSituation();

        assertTrue(CombatMapEntryTransitionPolicy.enteredMap("양조장", previous, after));
    }

    @Test
    void recognizes_a_map_bearing_opening_scene_without_waiting_for_a_later_turn() {
        Adventure after = adventure("양조장 지하 저장고", "맥주 저장고");

        assertTrue(CombatMapEntryTransitionPolicy.isMapBearing(after));
    }

    private static Adventure adventure(String scene, String location) {
        Adventure adventure = mock(Adventure.class);
        when(adventure.currentContext()).thenReturn(new AdventureContext(scene, null, null, null));
        when(adventure.currentSituation()).thenReturn(new CurrentSituation(UUID.randomUUID(), 1,
                location, "문제", "위협", "목표"));
        return adventure;
    }
}
