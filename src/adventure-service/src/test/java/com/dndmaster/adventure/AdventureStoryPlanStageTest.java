package com.dndmaster.adventure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.dndmaster.adventure.domain.adventure.AdventurePlanEvidence;
import com.dndmaster.adventure.domain.adventure.AdventureStageType;
import com.dndmaster.adventure.domain.adventure.AdventureStoryPlanStage;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AdventureStoryPlanStageTest {
    @Test
    void preserves_grounded_node_details_and_evidence() {
        UUID documentId = UUID.randomUUID();
        var evidence = new AdventurePlanEvidence("STORYBOOK", documentId, 3, "page:4", "The old well hides a key", .92);
        var stage = new AdventureStoryPlanStage(1, "The Well", "Find the key", "The well is guarded", "The key is recovered",
                List.of("Miller"), List.of("ending-a"), List.of(), AdventureStageType.DUNGEON, "Old Well", null, "", "",
                List.of("goblin"), "Goblin Keeper", "Recover the key", "The party retreats", List.of("silver key"), List.of("branch-a"), List.of(evidence));

        assertEquals(AdventureStageType.DUNGEON, stage.stageType());
        assertEquals("Old Well", stage.location());
        assertEquals(List.of("goblin"), stage.enemies());
        assertEquals("Goblin Keeper", stage.boss());
        assertEquals(evidence, stage.evidence().get(0));
        assertNull(stage.mapDefinitionId());
    }

    @Test
    void legacy_stage_defaults_to_event_without_grounding_fields() {
        var stage = new AdventureStoryPlanStage(1, "Opening", "Start", "Threat", "Lead", List.of(), List.of("ending-1"));

        assertEquals(AdventureStageType.EVENT, stage.stageType());
        assertEquals("Opening", stage.location());
        assertEquals("Lead", stage.clearCondition());
        assertEquals(List.of("ending-1"), stage.branchIds());
        assertEquals(List.of(), stage.evidence());
    }
}
