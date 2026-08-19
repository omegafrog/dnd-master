package com.dndmaster.adventure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.dndmaster.adventure.domain.adventure.*;
import com.dndmaster.adventure.application.runtime.TacticalTriggerEvaluator;
import java.util.List;
import org.junit.jupiter.api.Test;

class FutureTacticalSceneRevisionPolicyTest {
    @Test
    void preservesRevealedStagesAndCreatesSuccessorRevisionForFutureStages() {
        var plan = AdventureStoryPlan.ready(new SessionId(java.util.UUID.randomUUID()), 0, 1, List.of(stage(1, "published"), stage(2, "future"))).advanceTo(0);

        var next = plan.reviseFutureStages(List.of(stage(1, "published"), stage(2, "revised future")));

        assertEquals(3, next.version());
        assertEquals("published", next.stages().getFirst().title());
        assertEquals("revised future", next.stages().get(1).title());
    }

    @Test
    void rejectsRevisionOfPublishedStage() {
        var plan = AdventureStoryPlan.ready(new SessionId(java.util.UUID.randomUUID()), 0, 1, List.of(stage(1, "published"), stage(2, "future"))).advanceTo(0);

        assertThrows(IllegalStateException.class, () -> plan.reviseFutureStages(List.of(stage(1, "changed"), stage(2, "future"))));
    }

    @Test
    void evaluatesOnlyAnAuthoredTrigger() {
        var grounding = PlacementGrounding.aiInference("bounded");
        var scene = new TacticalScenePlan(1, TacticalScenePlanStatus.READY,
                new TacticalSceneBoundary(new NormalizedCoordinate(0, 0), new NormalizedCoordinate(1, 1), List.of()),
                List.of(new TacticalPlacement("hero", TacticalPlacementKind.PLAYER, new NormalizedCoordinate(.1, .1), grounding)),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), new FogPlan(List.of(), grounding),
                List.of(new TacticalTrigger("alarm", TacticalTriggerType.ALARM, List.of("hero"), "", grounding)), List.of(), List.of());
        var evaluator = new TacticalTriggerEvaluator();

        assertEquals("ALARM", evaluator.evaluate(scene, "alarm").type());
        assertThrows(IllegalArgumentException.class, () -> evaluator.evaluate(scene, "invented"));
    }

    private static AdventureStoryPlanStage stage(int position, String title) {
        return new AdventureStoryPlanStage(position, title, "goal", "conflict", "transition", List.of(), List.of("end"));
    }
}
