package com.dndmaster.adventure;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dndmaster.adventure.application.storyplan.AdventureStoryPlanGenerationPort;
import com.dndmaster.adventure.application.storyplan.TacticalScenePlanCandidate;
import com.dndmaster.adventure.application.storyplan.TacticalScenePlanValidator;
import com.dndmaster.adventure.application.storyplan.TacticalSceneRequest;
import com.dndmaster.adventure.domain.adventure.*;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TacticalScenePlanValidatorTest {
    @Test
    void requiresTriggerAndOutcomeCoverageForEveryMapBackedTacticalScene() {
        var source = new AdventureStoryPlanGenerationPort.SourceCitation("STORYBOOK", UUID.randomUUID(), 1,
                "page:1", "The cellar contains a rat swarm.", 1.0);
        var scene = new TacticalScenePlan(1, TacticalScenePlanStatus.READY,
                new TacticalSceneBoundary(new NormalizedCoordinate(0, 0), new NormalizedCoordinate(1, 1), List.of()),
                List.of(new TacticalPlacement("hero", TacticalPlacementKind.PLAYER, new NormalizedCoordinate(.1, .1), PlacementGrounding.sourceCitation(source.documentId() + ":page:1"))),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), new FogPlan(List.of(), PlacementGrounding.sourceCitation(source.documentId() + ":page:1")),
                List.of(), List.of(), List.of());
        var request = new TacticalSceneRequest(new AdventureStoryPlanStage(1, "cellar", "goal", "conflict", "exit", List.of(), List.of()),
                new AdventureStoryPlanGenerationPort.MapContext(UUID.randomUUID(), "map", "locator", "page:1", 1.0, "SAFE"), List.of(source), List.of());

        var violations = new TacticalScenePlanValidator().validate(request, new TacticalScenePlanCandidate(1, scene, List.of(source)));

        assertTrue(violations.contains("tactical scene requires explicit trigger coverage"));
    }
}
