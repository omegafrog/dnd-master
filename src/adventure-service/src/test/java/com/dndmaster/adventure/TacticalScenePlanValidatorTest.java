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

    @Test
    void requiresEveryMandatoryTacticalTriggerCategoryBeforeReady() {
        var evidence = evidence();
        var grounding = PlacementGrounding.sourceCitation(key(evidence));
        var scene = scene(
                List.of(new TacticalTrigger("entry", TacticalTriggerType.COMBAT_ENTRY,
                        List.of("hero"), "", grounding)),
                List.of(new TacticalOutcome("ending", "The party leaves safely.", grounding)),
                List.of(), grounding);

        var violations = validate(evidence, scene);

        assertTrue(violations.stream().anyMatch(value -> value.startsWith("tactical scene is missing required trigger types:")));
    }

    @Test
    void rejectsUnsupportedAiInferredRewardFactsButAllowsBoundedPlacementInference() {
        var evidence = evidence();
        var source = PlacementGrounding.sourceCitation(key(evidence));
        var inferred = PlacementGrounding.aiInference("Place an existing reward marker near the player");
        var triggers = new java.util.ArrayList<>(requiredTriggers(source));
        triggers.set(triggerIndex(triggers, TacticalTriggerType.REWARD),
                new TacticalTrigger("reward", TacticalTriggerType.REWARD, List.of("hero"), "", inferred));
        var scene = scene(triggers,
                List.of(new TacticalOutcome("ending", "The party leaves safely.", source)),
                List.of(), inferred);

        var violations = validate(evidence, scene);

        assertTrue(violations.contains("tactical reward requires source citation"));
    }

    @Test
    void rejectsUnsupportedAiInferredLootFacts() {
        var evidence = evidence();
        var source = PlacementGrounding.sourceCitation(key(evidence));
        var inferred = PlacementGrounding.aiInference("Add treasure not stated by the source");
        var scene = scene(requiredTriggers(source),
                List.of(new TacticalOutcome("ending", "The party leaves safely.", source)),
                List.of(new TacticalEnvironment("loot", "LOOT_CACHE", new NormalizedCoordinate(.6, .6), inferred)),
                source);

        var violations = validate(evidence, scene);

        assertTrue(violations.contains("tactical reward environment requires source citation"));
    }

    @Test
    void rejectsUnsupportedAiInferredEndingFacts() {
        var evidence = evidence();
        var source = PlacementGrounding.sourceCitation(key(evidence));
        var inferred = PlacementGrounding.aiInference("Invent a new ending");
        var scene = scene(requiredTriggers(source),
                List.of(new TacticalOutcome("ending", "The party rules this place.", inferred)),
                List.of(), source);

        var violations = validate(evidence, scene);

        assertTrue(violations.contains("tactical outcome requires source citation"));
    }

    @Test
    void rejectsUnsupportedAiInferredTransitions() {
        var evidence = evidence();
        var source = PlacementGrounding.sourceCitation(key(evidence));
        var inferred = PlacementGrounding.aiInference("Invent a transition");
        var triggers = new java.util.ArrayList<>(requiredTriggers(source));
        triggers.set(triggerIndex(triggers, TacticalTriggerType.ALARM),
                new TacticalTrigger("alarm", TacticalTriggerType.ALARM, List.of(), "new-ending", inferred));
        var scene = scene(triggers,
                List.of(new TacticalOutcome("ending", "The party leaves safely.", source)),
                List.of(), source, List.of("new-ending"));

        var violations = validate(evidence, scene);

        assertTrue(violations.contains("tactical transition requires source citation"));
    }

    @Test
    void allowsBoundedAiPlacementWhenCoreFactsAreSourceGrounded() {
        var evidence = evidence();
        var source = PlacementGrounding.sourceCitation(key(evidence));
        var inferred = PlacementGrounding.aiInference("Place the player at the mapped entrance");
        var scene = scene(requiredTriggers(source),
                List.of(new TacticalOutcome("ending", "The party leaves safely.", source)),
                List.of(), inferred);

        assertTrue(validate(evidence, scene).isEmpty());
    }

    private static AdventureStoryPlanGenerationPort.SourceCitation evidence() {
        return new AdventureStoryPlanGenerationPort.SourceCitation("STORYBOOK", UUID.randomUUID(), 1,
                "page:1", "The cellar has planned combat and a safe exit.", 1.0);
    }

    private static String key(AdventureStoryPlanGenerationPort.SourceCitation evidence) {
        return evidence.documentId() + ":" + evidence.locator();
    }

    private static List<String> validate(AdventureStoryPlanGenerationPort.SourceCitation evidence,
            TacticalScenePlan scene) {
        var request = new TacticalSceneRequest(
                new AdventureStoryPlanStage(1, "cellar", "goal", "conflict", "exit", List.of(), List.of()),
                new AdventureStoryPlanGenerationPort.MapContext(UUID.randomUUID(), "map", "locator", "page:1", 1.0, "SAFE"),
                List.of(evidence), List.of());
        return new TacticalScenePlanValidator().validate(
                request, new TacticalScenePlanCandidate(1, scene, List.of(evidence)));
    }

    private static TacticalScenePlan scene(List<TacticalTrigger> triggers, List<TacticalOutcome> outcomes,
            List<TacticalEnvironment> environments, PlacementGrounding placementGrounding) {
        return scene(triggers, outcomes, environments, placementGrounding, List.of());
    }

    private static TacticalScenePlan scene(List<TacticalTrigger> triggers, List<TacticalOutcome> outcomes,
            List<TacticalEnvironment> environments, PlacementGrounding placementGrounding,
            List<String> transitionIds) {
        return new TacticalScenePlan(1, TacticalScenePlanStatus.READY,
                new TacticalSceneBoundary(new NormalizedCoordinate(0, 0), new NormalizedCoordinate(1, 1), List.of()),
                List.of(new TacticalPlacement("hero", TacticalPlacementKind.PLAYER,
                        new NormalizedCoordinate(.1, .1), placementGrounding)),
                List.of(), List.of(), List.of(), List.of(), List.of(), environments,
                new FogPlan(List.of(), placementGrounding), triggers, outcomes, transitionIds);
    }

    private static List<TacticalTrigger> requiredTriggers(PlacementGrounding grounding) {
        return List.of(
                new TacticalTrigger("entry", TacticalTriggerType.COMBAT_ENTRY, List.of("hero"), "", grounding),
                new TacticalTrigger("alarm", TacticalTriggerType.ALARM, List.of(), "", grounding),
                new TacticalTrigger("reinforcement", TacticalTriggerType.REINFORCEMENT, List.of(), "", grounding),
                new TacticalTrigger("boss", TacticalTriggerType.BOSS, List.of(), "", grounding),
                new TacticalTrigger("reward", TacticalTriggerType.REWARD, List.of(), "", grounding),
                new TacticalTrigger("success", TacticalTriggerType.SUCCESS, List.of(), "", grounding),
                new TacticalTrigger("failure", TacticalTriggerType.FAILURE, List.of(), "", grounding),
                new TacticalTrigger("exit", TacticalTriggerType.EXIT, List.of(), "", grounding),
                new TacticalTrigger("surrender", TacticalTriggerType.SURRENDER, List.of(), "", grounding));
    }

    private static int triggerIndex(List<TacticalTrigger> triggers, TacticalTriggerType type) {
        for (int index = 0; index < triggers.size(); index++) {
            if (triggers.get(index).type() == type) return index;
        }
        throw new IllegalArgumentException("missing trigger type " + type);
    }
}
