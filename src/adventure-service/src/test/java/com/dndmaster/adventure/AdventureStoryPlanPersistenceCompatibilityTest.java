package com.dndmaster.adventure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dndmaster.adventure.domain.adventure.AdventureStoryPlanStage;
import com.dndmaster.adventure.domain.adventure.CombatParticipant;
import com.dndmaster.adventure.domain.adventure.CombatRequirement;
import com.dndmaster.adventure.domain.adventure.CombatSkeleton;
import com.dndmaster.adventure.domain.adventure.SourceFactClaim;
import com.dndmaster.adventure.domain.adventure.TacticalPreparationRequirement;
import com.dndmaster.adventure.domain.adventure.StageRole;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

class AdventureStoryPlanPersistenceCompatibilityTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void round_trips_v2_combat_fields_as_additive_stage_json() throws Exception {
        AdventureStoryPlanStage stage = new AdventureStoryPlanStage(1, "폐허", "쥐를 막는다", "거대 쥐가 나타난다", "쥐를 물리치면 이동한다", List.of(), List.of("ending-1"))
                .withCombat(CombatRequirement.REQUIRED,
                        new CombatSkeleton("쥐를 막는다", "쥐가 나타나면 전투를 시작한다",
                                List.of(new CombatParticipant("rat", CombatParticipant.Role.ENEMY, "거대 쥐", 2, 2, List.of("rat-fact"))),
                                "쥐를 물리친다", "후퇴하고 다음 단서를 확보한다",
                                List.of(new SourceFactClaim("combatSkeleton.rewards[0]", "쥐의 둥지 단서", "rat-fact"))),
                        List.of(new SourceFactClaim("combatSkeleton.participants[0].name", "거대 쥐", "rat-fact")),
                        TacticalPreparationRequirement.REQUIRED);

        String json = mapper.writeValueAsString(List.of(stage));
        List<AdventureStoryPlanStage> restored = mapper.readValue(json, new TypeReference<>() {});

        AdventureStoryPlanStage actual = restored.getFirst();
        assertEquals(2, actual.schemaVersion());
        assertEquals(CombatRequirement.REQUIRED, actual.combatRequirement());
        assertEquals("거대 쥐", actual.combatSkeleton().participants().getFirst().name());
        assertEquals(TacticalPreparationRequirement.REQUIRED, actual.tacticalPreparationRequirement());
        assertEquals("rat-fact", actual.sourceFactClaims().getFirst().citationKeys().getFirst());
    }

    @Test
    void reads_legacy_stage_json_with_safe_v2_defaults() throws Exception {
        String legacyJson = "[{\"position\":1,\"title\":\"마을\",\"goal\":\"단서를 찾는다\",\"conflict\":\"소문이 엇갈린다\",\"transitionCondition\":\"단서를 찾으면 이동한다\",\"npcOrClues\":[],\"endingIds\":[\"ending-1\"]}]";

        AdventureStoryPlanStage restored = mapper.readValue(legacyJson, new TypeReference<List<AdventureStoryPlanStage>>() {}).getFirst();

        assertEquals(1, restored.schemaVersion());
        assertEquals(CombatRequirement.NONE, restored.combatRequirement());
        assertTrue(restored.combatSkeleton().participants().isEmpty());
        assertEquals(TacticalPreparationRequirement.NOT_REQUIRED, restored.tacticalPreparationRequirement());
        assertEquals(StageRole.NORMAL, restored.stageRole());
    }
}
