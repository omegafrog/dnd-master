package com.dndmaster.adventure.domain.adventure;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Immutable, source-image-relative tactical intent persisted with a story-plan stage. */
public record TacticalScenePlan(int schemaVersion, TacticalScenePlanStatus status, TacticalSceneBoundary boundary,
        List<TacticalPlacement> players, List<TacticalPlacement> allies, List<TacticalPlacement> npcs,
        List<TacticalPlacement> enemies, List<TacticalPlacement> bosses, List<TacticalPlacement> interactiveObjects,
        List<TacticalEnvironment> environments, FogPlan initialFog, List<TacticalTrigger> triggers,
        List<TacticalOutcome> outcomes, List<String> transitionIds) {
    public static final int CURRENT_SCHEMA_VERSION = 1;

    public TacticalScenePlan {
        status = Objects.requireNonNull(status, "tactical scene status must not be null");
        if (status == TacticalScenePlanStatus.ABSENT) {
            if (schemaVersion != 0) throw new IllegalArgumentException("absent tactical scene must use schema version 0");
            boundary = null;
            players = List.of(); allies = List.of(); npcs = List.of(); enemies = List.of(); bosses = List.of();
            interactiveObjects = List.of(); environments = List.of(); initialFog = null; triggers = List.of(); outcomes = List.of(); transitionIds = List.of();
        } else {
            if (schemaVersion != CURRENT_SCHEMA_VERSION) throw new IllegalArgumentException("unsupported tactical scene schema version");
            boundary = Objects.requireNonNull(boundary, "tactical scene boundary must not be null");
            players = immutable(players, "players");
            allies = immutable(allies, "allies");
            npcs = immutable(npcs, "npcs");
            enemies = immutable(enemies, "enemies");
            bosses = immutable(bosses, "bosses");
            interactiveObjects = immutable(interactiveObjects, "interactive objects");
            environments = immutable(environments, "environments");
            initialFog = Objects.requireNonNull(initialFog, "initial fog must not be null");
            triggers = immutable(triggers, "triggers");
            outcomes = immutable(outcomes, "outcomes");
            transitionIds = immutableStrings(transitionIds, "transition ids");
            validateKinds(players, TacticalPlacementKind.PLAYER);
            validateKinds(allies, TacticalPlacementKind.ALLY);
            validateKinds(npcs, TacticalPlacementKind.NPC);
            validateKinds(enemies, TacticalPlacementKind.ENEMY);
            validateKinds(bosses, TacticalPlacementKind.BOSS);
            validateKinds(interactiveObjects, TacticalPlacementKind.INTERACTIVE_OBJECT);
            validateLocations(boundary, allPlacements(players, allies, npcs, enemies, bosses, interactiveObjects), environments);
            validateTriggerReferences(triggers, allIds(allPlacements(players, allies, npcs, enemies, bosses, interactiveObjects), environments), transitionIds);
        }
    }

    public static TacticalScenePlan absent() {
        return new TacticalScenePlan(0, TacticalScenePlanStatus.ABSENT, null, null, null, null, null, null, null, null, null, null, null, null);
    }

    public boolean readyForActivation() {
        return status == TacticalScenePlanStatus.READY;
    }

    private static <T> List<T> immutable(List<T> values, String category) {
        return List.copyOf(Objects.requireNonNull(values, category + " must be explicit"));
    }

    private static List<String> immutableStrings(List<String> values, String category) {
        List<String> result = List.copyOf(Objects.requireNonNull(values, category + " must be explicit"));
        if (result.stream().anyMatch(value -> value == null || value.isBlank())) throw new IllegalArgumentException(category + " must not contain blanks");
        return result;
    }

    private static void validateKinds(List<TacticalPlacement> values, TacticalPlacementKind expected) {
        if (values.stream().anyMatch(value -> value.kind() != expected)) {
            throw new IllegalArgumentException("tactical placement is in the wrong category");
        }
    }

    @SafeVarargs
    private static List<TacticalPlacement> allPlacements(List<TacticalPlacement>... categories) {
        List<TacticalPlacement> result = new ArrayList<>();
        for (List<TacticalPlacement> category : categories) result.addAll(category);
        return result;
    }

    private static void validateLocations(TacticalSceneBoundary boundary, List<TacticalPlacement> placements,
            List<TacticalEnvironment> environments) {
        Set<String> ids = new HashSet<>();
        Set<NormalizedCoordinate> coordinates = new HashSet<>();
        for (TacticalPlacement placement : placements) {
            if (!ids.add(placement.id())) throw new IllegalArgumentException("tactical entity ids must be unique");
            validateCoordinate(boundary, coordinates, placement.coordinate());
        }
        for (TacticalEnvironment environment : environments) {
            if (!ids.add(environment.id())) throw new IllegalArgumentException("tactical entity ids must be unique");
            validateCoordinate(boundary, coordinates, environment.coordinate());
        }
    }

    private static void validateCoordinate(TacticalSceneBoundary boundary, Set<NormalizedCoordinate> coordinates,
            NormalizedCoordinate coordinate) {
        if (!boundary.contains(coordinate)) throw new IllegalArgumentException("tactical coordinate is outside the stage boundary");
        if (boundary.forbiddenCoordinates().contains(coordinate)) throw new IllegalArgumentException("tactical coordinate is forbidden");
        if (!coordinates.add(coordinate)) throw new IllegalArgumentException("tactical coordinates must not collide");
    }

    private static Set<String> allIds(List<TacticalPlacement> placements, List<TacticalEnvironment> environments) {
        Set<String> result = new HashSet<>();
        placements.forEach(value -> result.add(value.id()));
        environments.forEach(value -> result.add(value.id()));
        return result;
    }

    private static void validateTriggerReferences(List<TacticalTrigger> triggers, Set<String> ids, List<String> transitionIds) {
        Set<String> triggerIds = new HashSet<>();
        for (TacticalTrigger trigger : triggers) {
            if (!triggerIds.add(trigger.id())) throw new IllegalArgumentException("tactical trigger ids must be unique");
            if (!ids.containsAll(trigger.targetIds())) throw new IllegalArgumentException("tactical trigger references an unknown entity");
            if (!trigger.transitionId().isBlank() && !transitionIds.contains(trigger.transitionId())) {
                throw new IllegalArgumentException("tactical trigger references an unknown transition");
            }
        }
    }
}
