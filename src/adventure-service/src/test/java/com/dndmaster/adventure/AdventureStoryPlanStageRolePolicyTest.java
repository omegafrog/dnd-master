package com.dndmaster.adventure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dndmaster.adventure.application.storyplan.AdventureStoryPlanStageRolePolicy;
import com.dndmaster.adventure.domain.adventure.AdventureStageType;
import com.dndmaster.adventure.domain.adventure.AdventureStoryPlanStage;
import com.dndmaster.adventure.domain.adventure.StageRole;
import com.dndmaster.adventure.domain.knowledge.KnowledgeDocumentId;
import com.dndmaster.adventure.domain.scenario.ScenarioEntryResult;
import java.util.List;
import org.junit.jupiter.api.Test;

class AdventureStoryPlanStageRolePolicyTest {
    @Test
    void materializes_one_minimal_prologue_and_preserves_normal_stage_types() {
        var entry = new ScenarioEntryResult(
                ScenarioEntryResult.Decision.MINIMAL_PROLOGUE,
                "A small settlement near the Sword Coast",
                "The party is looking for a safe place to begin.",
                List.of(), "Sword Coast");
        var dungeon = new AdventureStoryPlanStage(1, "Ruined cellar", "Find the entrance", "A blocked door", "Open it",
                List.of(), List.of("ending-1"), List.of(), AdventureStageType.DUNGEON, "Ruined cellar", null, "", "",
                List.of(), "", "Open it", "", List.of(), List.of("ending-1"), List.of(),
                com.dndmaster.adventure.domain.adventure.AdventureGroundingStatus.GROUNDED, List.of(), "UNAVAILABLE", null);

        var materialized = AdventureStoryPlanStageRolePolicy.materialize(entry, List.of(dungeon));

        assertEquals(2, materialized.size());
        assertEquals(StageRole.PROLOGUE, materialized.getFirst().stageRole());
        assertEquals(AdventureStageType.EVENT, materialized.getFirst().stageType());
        assertTrue(materialized.getFirst().location().contains("Sword Coast"));
        assertEquals(2, materialized.get(1).position());
        assertEquals(AdventureStageType.DUNGEON, materialized.get(1).stageType());
        assertEquals(StageRole.NORMAL, materialized.get(1).stageRole());
    }

    @Test
    void leaves_reliable_source_entry_as_normal_stages() {
        var entry = new ScenarioEntryResult(
                ScenarioEntryResult.Decision.INFERRED_SOURCE, "The cellar", "The party arrives.",
                List.of(new com.dndmaster.adventure.domain.scenario.ScenarioSourceReference(
                        new KnowledgeDocumentId(java.util.UUID.randomUUID()), 1, "page:1")), "cellar");
        var stage = new AdventureStoryPlanStage(1, "The cellar", "Explore", "Rats", "Continue", List.of(), List.of("ending-1"));

        var materialized = AdventureStoryPlanStageRolePolicy.materialize(entry, List.of(stage));

        assertEquals(List.of(stage), materialized);
        assertEquals(StageRole.NORMAL, materialized.getFirst().stageRole());
    }

    @Test
    void rejects_prologue_without_anchor_or_with_campaign_scale_content() {
        var prologue = AdventureStoryPlanStageRolePolicy.prologue(
                new ScenarioEntryResult(ScenarioEntryResult.Decision.MINIMAL_PROLOGUE,
                        "A roadside camp", "The party seeks shelter.", List.of(), "Sword Coast"));
        var expanded = new AdventureStoryPlanStage(1, "Sword Coast", "Start the main quest", "The villain reveals a secret faction campaign",
                "Choose a side", List.of(), List.of("ending-1"));
        var violations = AdventureStoryPlanStageRolePolicy.validatePrologue(expanded, "Sword Coast");

        assertTrue(AdventureStoryPlanStageRolePolicy.validatePrologue(prologue, "Sword Coast").isEmpty());
        assertTrue(violations.contains("prologue must remain connector-scale"));
    }
}
