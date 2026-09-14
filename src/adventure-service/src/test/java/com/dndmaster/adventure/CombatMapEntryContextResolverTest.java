package com.dndmaster.adventure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.dndmaster.adventure.application.combat.CombatMapEntryContextResolver;
import com.dndmaster.adventure.domain.adventure.Adventure;
import com.dndmaster.adventure.domain.adventure.AdventureContext;
import com.dndmaster.adventure.domain.runtime.CurrentSituation;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CombatMapEntryContextResolverTest {
    @Test
    void basement_situation_enters_from_the_hatch_side_instead_of_center_fallback() {
        Adventure adventure = mock(Adventure.class);
        when(adventure.currentContext()).thenReturn(new AdventureContext("거대 쥐가 드러난 지하 맥주 저장고", null, null, null));
        CurrentSituation situation = new CurrentSituation(UUID.randomUUID(), 7, "지하 맥주 저장고",
                "거대 쥐들이 저장고를 점거했다.", "위치가 드러난 거대 쥐 여덟 마리", "거대 쥐를 처치한다.");

        assertEquals("NORTH", CombatMapEntryContextResolver.entrySide(adventure, situation));
    }

    @Test
    void explicit_direction_wins_over_generic_location_words() {
        Adventure adventure = mock(Adventure.class);
        when(adventure.currentContext()).thenReturn(new AdventureContext("동쪽 통로로 들어간다", null, null, null));
        CurrentSituation situation = new CurrentSituation(UUID.randomUUID(), 1, "지하 저장고",
                "문제", "위협", "목표");

        assertEquals("EAST", CombatMapEntryContextResolver.entrySide(adventure, situation));
    }

    @Test
    void situation_without_an_entry_cue_does_not_invent_a_direction() {
        Adventure adventure = mock(Adventure.class);
        when(adventure.currentContext()).thenReturn(new AdventureContext("광장", null, null, null));
        CurrentSituation situation = new CurrentSituation(UUID.randomUUID(), 1, "광장",
                "문제", "위협", "목표");

        assertNull(CombatMapEntryContextResolver.entrySide(adventure, situation));
    }
}
