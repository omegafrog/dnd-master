package com.dndmaster.adventure;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dndmaster.adventure.application.runtime.AdventureStoryPlanRuntimeContext;
import com.dndmaster.adventure.domain.adventure.AdventureGroundingStatus;
import com.dndmaster.adventure.domain.adventure.AdventurePlanConfiguration;
import com.dndmaster.adventure.domain.adventure.AdventureStoryPlan;
import com.dndmaster.adventure.domain.adventure.AdventureStoryPlanStage;
import com.dndmaster.adventure.domain.adventure.AdventureStageType;
import com.dndmaster.adventure.domain.adventure.SessionId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AdventureStoryPlanRuntimeContextTest {
    @Test
    void exposes_current_stage_and_branch_candidates_to_gm() {
        var stage = new AdventureStoryPlanStage(1, "The Well", "Find the key", "The well is guarded", "The key is recovered",
                List.of(), List.of("ending-a"), List.of(), AdventureStageType.DUNGEON, "Old Well", null, "well-map", "maps/well.png",
                List.of("goblin"), "Goblin Keeper", "Recover the key", "The party retreats", List.of("silver key"), List.of("branch-a", "branch-b"),
                List.of(), AdventureGroundingStatus.AI_SUGGESTION, List.of("boss"), "SAFE", .9);
        var plan = AdventureStoryPlan.ready(UUID.randomUUID(), new SessionId(UUID.randomUUID()), 1, 1, 2,
                new AdventurePlanConfiguration(2, com.dndmaster.adventure.domain.adventure.AdventureLength.SHORT), List.of(stage));

        String context = AdventureStoryPlanRuntimeContext.format(plan);

        assertTrue(context.contains("currentStage=1"));
        assertTrue(context.contains("availableBranches=branch-a,branch-b"));
        assertTrue(context.contains("map=well-map@maps/well.png"));
        assertTrue(context.contains("grounding=AI_SUGGESTION"));
    }

    @Test
    void advances_plan_without_rewriting_authored_nodes() {
        var first = new AdventureStoryPlanStage(1, "Town", "Rest", "Storm", "Leave", List.of(), List.of("ending-a"));
        var second = new AdventureStoryPlanStage(2, "Dungeon", "Enter", "Guards", "Win", List.of(), List.of("ending-a"));
        var plan = AdventureStoryPlan.ready(UUID.randomUUID(), new SessionId(UUID.randomUUID()), 1, 1, 2,
                new AdventurePlanConfiguration(1, com.dndmaster.adventure.domain.adventure.AdventureLength.SHORT), List.of(first, second));

        var advanced = plan.advanceTo(1);

        assertTrue(advanced.currentStage() == 1);
        assertTrue(advanced.stages().get(0).title().equals("Town"));
        assertTrue(advanced.version() == 3);
    }
}
