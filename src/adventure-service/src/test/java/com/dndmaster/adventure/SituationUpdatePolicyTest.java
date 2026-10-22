package com.dndmaster.adventure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.dndmaster.adventure.application.runtime.SituationUpdatePolicy;
import com.dndmaster.adventure.application.runtime.SituationUpdateProposal;
import com.dndmaster.adventure.domain.runtime.CurrentSituation;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SituationUpdatePolicyTest {
    @Test
    void new_situation_title_description_and_map_are_persisted_with_new_identity() {
        CurrentSituation current = CurrentSituation.initial("저장고의 맥주통을 살핀다");

        CurrentSituation next = SituationUpdatePolicy.apply(current,
                SituationUpdateProposal.transition("양조장 뒤뜰", "정찰병이 순찰 중이다", "정찰병이 발견할 수 있다", "정찰병의 움직임을 파악한다"),
                "고블린 정찰병이 순찰 중", "고블린 정찰병 하나가 뒤뜰을 순찰하며 주변을 살핀다.", null);

        org.junit.jupiter.api.Assertions.assertNotEquals(current.situationId(), next.situationId());
        org.junit.jupiter.api.Assertions.assertEquals(1, next.revision());
        org.junit.jupiter.api.Assertions.assertEquals("고블린 정찰병이 순찰 중", next.title());
        org.junit.jupiter.api.Assertions.assertTrue(next.description().contains("뒤뜰"));
        org.junit.jupiter.api.Assertions.assertNull(next.mapDefinitionId());
    }

    @Test
    void transition_preserves_current_values_for_omitted_situation_elements() {
        CurrentSituation current = new CurrentSituation(UUID.randomUUID(), 1,
                "cellar", "find the source", "rats are nearby", "secure the room");

        CurrentSituation updated = SituationUpdatePolicy.apply(current,
                SituationUpdateProposal.transition(null, "blocked by the rat swarm", null, null));

        assertNotEquals(current.situationId(), updated.situationId());
        assertEquals("cellar", updated.location());
        assertEquals("blocked by the rat swarm", updated.problem());
        assertEquals("rats are nearby", updated.threat());
        assertEquals("secure the room", updated.goal());
    }
}
