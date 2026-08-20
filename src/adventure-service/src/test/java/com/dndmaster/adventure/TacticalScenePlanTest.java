package com.dndmaster.adventure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.dndmaster.adventure.domain.adventure.AdventureStoryPlanStage;
import com.dndmaster.adventure.domain.adventure.FogPlan;
import com.dndmaster.adventure.domain.adventure.NormalizedCoordinate;
import com.dndmaster.adventure.domain.adventure.PlacementGrounding;
import com.dndmaster.adventure.domain.adventure.TacticalPlacement;
import com.dndmaster.adventure.domain.adventure.TacticalPlacementKind;
import com.dndmaster.adventure.domain.adventure.TacticalSceneBoundary;
import com.dndmaster.adventure.domain.adventure.TacticalScenePlan;
import com.dndmaster.adventure.domain.adventure.TacticalScenePlanStatus;
import com.dndmaster.adventure.domain.adventure.TacticalTrigger;
import com.dndmaster.adventure.domain.adventure.TacticalTriggerType;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import com.dndmaster.adventure.application.runtime.TacticalTriggerEvaluator;
import com.dndmaster.adventure.application.runtime.TacticalTriggerRuntimeApplicationService;

class TacticalScenePlanTest {
    @Test
    void evaluatesAuthoredTriggerAndAppliesItsEffectToTheRuntimeSeam() {
        var scene = new TacticalScenePlan(TacticalScenePlan.CURRENT_SCHEMA_VERSION, TacticalScenePlanStatus.READY, boundary(),
                List.of(placement("party", TacticalPlacementKind.PLAYER, .1, .1)), List.of(), List.of(),
                List.of(placement("enemy-1", TacticalPlacementKind.ENEMY, .8, .8)), List.of(), List.of(), List.of(),
                new FogPlan(List.of(), grounding("fog")),
                List.of(new TacticalTrigger("entry", TacticalTriggerType.COMBAT_ENTRY, List.of("enemy-1"), "", grounding("entry"))),
                List.of(), List.of());
        var seen = new TacticalTriggerEvaluator.Evaluation[1];
        var service = new TacticalTriggerRuntimeApplicationService(new TacticalTriggerEvaluator(),
                (mapId, ownerId, version, commandId, evaluation) -> seen[0] = evaluation,
                (adventureId, stagePosition, ownerId) -> java.util.Optional.empty());

        service.apply(scene, "entry", UUID.randomUUID(), UUID.randomUUID(), 0, UUID.randomUUID());

        assertEquals(List.of("enemy-1"), seen[0].targetIds());
        assertEquals("COMBAT_ENTRY", seen[0].type());
    }

    @Test
    void rejectsPlayerActionThatDoesNotQualifyTheAuthoredTrigger() {
        var scene = new TacticalScenePlan(TacticalScenePlan.CURRENT_SCHEMA_VERSION, TacticalScenePlanStatus.READY, boundary(),
                List.of(placement("party", TacticalPlacementKind.PLAYER, .1, .1)), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                new FogPlan(List.of(), grounding("fog")),
                List.of(new TacticalTrigger("entry", TacticalTriggerType.COMBAT_ENTRY, List.of(), "", grounding("entry"))), List.of(), List.of());
        var evaluator = new TacticalTriggerEvaluator();
        assertThrows(IllegalArgumentException.class, () -> evaluator.evaluate(scene, "entry", "reward"));
        assertEquals("COMBAT_ENTRY", evaluator.evaluate(scene, "entry", "combat_entry").type());
    }

    @Test
    void requiresAQualifyingActionAtThePlayerScopedRuntimeBoundary() {
        var scene = new TacticalScenePlan(TacticalScenePlan.CURRENT_SCHEMA_VERSION, TacticalScenePlanStatus.READY, boundary(),
                List.of(placement("party", TacticalPlacementKind.PLAYER, .1, .1)), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                new FogPlan(List.of(), grounding("fog")),
                List.of(new TacticalTrigger("entry", TacticalTriggerType.COMBAT_ENTRY, List.of(), "", grounding("entry"))), List.of(), List.of());
        var activeMap = UUID.randomUUID();
        var service = new TacticalTriggerRuntimeApplicationService(new TacticalTriggerEvaluator(), (map, owner, version, command, evaluation) -> { },
                (adventureId, stagePosition, ownerId) -> java.util.Optional.of(activeMap));
        assertThrows(IllegalArgumentException.class, () -> service.apply(UUID.randomUUID(), 1, scene, "entry", null,
                activeMap, UUID.randomUUID(), 0, UUID.randomUUID()));
    }

    @Test
    void rejectsAMapThatIsNotTheActiveMapForTheAdventureStage() {
        var scene = new TacticalScenePlan(1, TacticalScenePlanStatus.READY, boundary(),
                List.of(placement("party", TacticalPlacementKind.PLAYER, .1, .1)), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                new FogPlan(List.of(), grounding("fog")),
                List.of(new TacticalTrigger("entry", TacticalTriggerType.COMBAT_ENTRY, List.of(), "", grounding("entry"))), List.of(), List.of());
        var activeMap = UUID.randomUUID();
        var service = new TacticalTriggerRuntimeApplicationService(new TacticalTriggerEvaluator(), (map, owner, version, command, evaluation) -> { },
                (adventureId, stagePosition, ownerId) -> java.util.Optional.of(activeMap));
        var adventure = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class, () -> service.apply(adventure, 1, scene, "entry", UUID.randomUUID(), UUID.randomUUID(), 0, UUID.randomUUID()));
    }

    @Test
    void acceptsTheDurablyOwnedActiveMapAfterRuntimeServiceRecreation() {
        var activeMap = UUID.randomUUID();
        var adventure = UUID.randomUUID();
        var owner = UUID.randomUUID();
        var scene = new TacticalScenePlan(1, TacticalScenePlanStatus.READY, boundary(),
                List.of(placement("party", TacticalPlacementKind.PLAYER, .1, .1)), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                new FogPlan(List.of(), grounding("fog")), List.of(new TacticalTrigger("entry", TacticalTriggerType.COMBAT_ENTRY, List.of(), "", grounding("entry"))), List.of(), List.of());
        var seen = new TacticalTriggerEvaluator.Evaluation[1];
        var service = new TacticalTriggerRuntimeApplicationService(new TacticalTriggerEvaluator(), (map, player, version, command, evaluation) -> seen[0] = evaluation,
                (adventureId, stagePosition, ownerId) -> java.util.Optional.of(activeMap));

        service.apply(adventure, 1, scene, "entry", "combat_entry", activeMap, owner, 0, UUID.randomUUID());

        assertEquals("COMBAT_ENTRY", seen[0].type());
    }

    @Test
    void bindingStoresMapPerAdventureStageAndOwnerWithoutLatestMapFallback() {
        var bindings = new java.util.HashMap<String, UUID>();
        var port = new com.dndmaster.adventure.application.runtime.ActiveTacticalMapPort() {
            public java.util.Optional<UUID> findActiveMap(UUID adventureId, int stagePosition, UUID ownerPlayerId) {
                return java.util.Optional.ofNullable(bindings.get(adventureId + ":" + stagePosition + ":" + ownerPlayerId));
            }
            public void bindActiveMap(UUID adventureId, int stagePosition, UUID ownerPlayerId, UUID combatMapId) {
                bindings.put(adventureId + ":" + stagePosition + ":" + ownerPlayerId, combatMapId);
            }
        };
        var service = new TacticalTriggerRuntimeApplicationService(new TacticalTriggerEvaluator(), (map, owner, version, command, evaluation) -> { }, port);
        var adventure = UUID.randomUUID();
        var owner = UUID.randomUUID();
        var stageOne = UUID.randomUUID();
        var stageTwo = UUID.randomUUID();
        service.bindActiveMap(adventure, 1, owner, stageOne);
        service.bindActiveMap(adventure, 2, owner, stageTwo);
        assertEquals(java.util.Optional.of(stageOne), port.findActiveMap(adventure, 1, owner));
        assertEquals(java.util.Optional.of(stageTwo), port.findActiveMap(adventure, 2, owner));
        assertEquals(java.util.Optional.empty(), port.findActiveMap(adventure, 3, owner));
    }
    @Test
    void rejectsNormalizedCoordinatesOutsideTheSourceMap() {
        assertThrows(IllegalArgumentException.class, () -> new NormalizedCoordinate(1.01, .5));
    }

    @Test
    void requiresEveryTacticalCategoryToBeExplicitEvenWhenEmpty() {
        assertThrows(NullPointerException.class, () -> new TacticalScenePlan(
                TacticalScenePlan.CURRENT_SCHEMA_VERSION, TacticalScenePlanStatus.READY, boundary(),
                List.of(placement("player", TacticalPlacementKind.PLAYER, .1, .1)), null, List.of(), List.of(), List.of(),
                List.of(), List.of(), new FogPlan(List.of(), grounding("fog")), List.of(), List.of(), List.of("leave")));
    }

    @Test
    void rejectsCollidingAndForbiddenPlacements() {
        assertThrows(IllegalArgumentException.class, () -> new TacticalScenePlan(
                TacticalScenePlan.CURRENT_SCHEMA_VERSION, TacticalScenePlanStatus.READY, boundary(),
                List.of(placement("player", TacticalPlacementKind.PLAYER, .1, .1)), List.of(),
                List.of(placement("guard", TacticalPlacementKind.NPC, .1, .1)), List.of(), List.of(), List.of(), List.of(),
                new FogPlan(List.of(), grounding("fog")), List.of(), List.of(), List.of("leave")));

        assertThrows(IllegalArgumentException.class, () -> new TacticalScenePlan(
                TacticalScenePlan.CURRENT_SCHEMA_VERSION, TacticalScenePlanStatus.READY,
                new TacticalSceneBoundary(new NormalizedCoordinate(0, 0), new NormalizedCoordinate(1, 1), List.of(new NormalizedCoordinate(.2, .2))),
                List.of(placement("player", TacticalPlacementKind.PLAYER, .2, .2)), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                new FogPlan(List.of(), grounding("fog")), List.of(), List.of(), List.of("leave")));
    }

    @Test
    void rejectsTriggersThatTargetUnknownEntitiesOrTransitions() {
        assertThrows(IllegalArgumentException.class, () -> new TacticalScenePlan(
                TacticalScenePlan.CURRENT_SCHEMA_VERSION, TacticalScenePlanStatus.READY, boundary(),
                List.of(placement("player", TacticalPlacementKind.PLAYER, .1, .1)), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                new FogPlan(List.of(), grounding("fog")),
                List.of(new TacticalTrigger("entry", TacticalTriggerType.COMBAT_ENTRY, List.of("unknown"), "missing", grounding("trigger"))),
                List.of(), List.of("leave")));
    }

    @Test
    void deserializesLegacyStagesAsAbsentTacticalPlans() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        var legacyStage = new AdventureStoryPlanStage(1, "Opening", "Start", "Threat", "Leave", List.of(), List.of("ending"));
        var json = (com.fasterxml.jackson.databind.node.ObjectNode) mapper.valueToTree(legacyStage);
        json.remove("tacticalScenePlan");

        var restored = mapper.treeToValue(json, AdventureStoryPlanStage.class);

        assertEquals(TacticalScenePlanStatus.ABSENT, restored.tacticalScenePlan().status());
    }

    private static TacticalSceneBoundary boundary() {
        return new TacticalSceneBoundary(new NormalizedCoordinate(0, 0), new NormalizedCoordinate(1, 1), List.of());
    }

    private static TacticalPlacement placement(String id, TacticalPlacementKind kind, double x, double y) {
        return new TacticalPlacement(id, kind, new NormalizedCoordinate(x, y), grounding(id));
    }

    private static PlacementGrounding grounding(String source) {
        return PlacementGrounding.aiInference(source + " is needed for this scene");
    }
}
