package com.dndmaster.combatmap.application.spatial;

import com.dndmaster.combatmap.domain.CombatMap;
import com.dndmaster.combatmap.domain.GridPosition;
import com.dndmaster.combatmap.domain.SpatialFeature;
import com.dndmaster.combatmap.domain.SpatialFeatureVisibility;
import com.dndmaster.combatmap.domain.SpatialTrigger;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** 허용된 공간 발동만 적용하고 공개 결과에서는 내부 식별자를 제거한다. */
public final class SpatialTriggerResolver {
    public List<String> resolve(CombatMap map, SpatialTrigger trigger, GridPosition cell) {
        Objects.requireNonNull(map, "combat map must not be null");
        Objects.requireNonNull(trigger, "spatial trigger must not be null");
        Objects.requireNonNull(cell, "trigger cell must not be null");
        List<String> events = new ArrayList<>();
        for (SpatialFeature feature : map.spatialFeatures()) {
            events.addAll(resolveFeature(feature, trigger, cell));
        }
        return List.copyOf(events);
    }

    public List<String> resolveFeatureInteraction(CombatMap map, UUID featureId) {
        SpatialFeature feature = validateFeatureInteraction(map, featureId);
        GridPosition cell = feature.cells().stream().sorted(Comparator.comparingInt(GridPosition::x)
                .thenComparingInt(GridPosition::y)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("spatial feature has no cells"));
        return resolveFeature(feature, SpatialTrigger.INTERACT, cell);
    }

    public SpatialFeature validateFeatureInteraction(CombatMap map, UUID featureId) {
        Objects.requireNonNull(map, "combat map must not be null");
        Objects.requireNonNull(featureId, "spatial feature id must not be null");
        SpatialFeature feature = map.spatialFeatures().stream().filter(candidate -> candidate.id().equals(featureId))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("spatial feature does not exist"));
        if (!feature.triggers().contains(SpatialTrigger.INTERACT))
            throw new IllegalArgumentException("spatial feature does not allow interaction");
        if (feature.type() == com.dndmaster.combatmap.domain.SpatialFeatureType.MAGICAL_AREA_EFFECT)
            throw new IllegalArgumentException("magical area effects do not support interaction");
        if (!feature.canTrigger() || feature.visibility() == SpatialFeatureVisibility.HIDDEN)
            throw new IllegalArgumentException("spatial feature is not currently interactable");
        if (feature.cells().isEmpty()) throw new IllegalArgumentException("spatial feature has no cells");
        return feature;
    }

    /** Applies an observation only to features whose player-owned check succeeded. */
    public List<String> resolveObserved(CombatMap map, SpatialTrigger trigger, GridPosition cell,
            java.util.Set<java.util.UUID> successfulFeatureIds) {
        Objects.requireNonNull(successfulFeatureIds, "successful feature ids must not be null");
        List<String> events = new ArrayList<>();
        for (SpatialFeature feature : map.spatialFeatures()) {
            if (!feature.cells().contains(cell) || !feature.triggers().contains(trigger)) continue;
            if (feature.visibility() == SpatialFeatureVisibility.HIDDEN
                    && !successfulFeatureIds.contains(feature.id())) continue;
            if (feature.visibility() != SpatialFeatureVisibility.HIDDEN) continue;
            feature.discover();
            events.add(eventName(feature, trigger, cell));
        }
        return List.copyOf(events);
    }

    /** 이동 해결 재생용 공개 trigger. 숨겨진 요소를 실패한 탐지로 공개하지 않는다. */
    public List<String> resolveVisible(CombatMap map, GridPosition cell) {
        Objects.requireNonNull(map, "combat map must not be null");
        Objects.requireNonNull(cell, "trigger cell must not be null");
        return resolveVisible(map, List.of(cell));
    }

    /** Newly visible cells are one observation; a multi-cell feature fires once by feature id. */
    public List<String> resolveVisible(CombatMap map, java.util.Collection<GridPosition> cells) {
        return resolveVisible(map, cells, Set.of());
    }

    /**
     * Resolves visibility for eligible hidden features. Features excluded by a
     * failed explicit detection stay hidden, so a visibility refresh cannot
     * leak the failed check.
     */
    public List<String> resolveVisible(CombatMap map, java.util.Collection<GridPosition> cells,
            Set<UUID> excludedFeatureIds) {
        Objects.requireNonNull(map, "combat map must not be null");
        Objects.requireNonNull(cells, "visible cells must not be null");
        Objects.requireNonNull(excludedFeatureIds, "excluded feature ids must not be null");
        List<String> events = new ArrayList<>();
        Set<UUID> resolvedFeatureIds = new HashSet<>();
        List<GridPosition> orderedCells = cells.stream().filter(Objects::nonNull).distinct()
                .sorted(Comparator.comparingInt(GridPosition::x).thenComparingInt(GridPosition::y)).toList();
        for (GridPosition cell : orderedCells) {
            for (SpatialFeature feature : map.spatialFeatures()) {
                if (!feature.cells().contains(cell) || !feature.triggers().contains(SpatialTrigger.BECOME_VISIBLE)
                        || feature.visibility() != SpatialFeatureVisibility.HIDDEN
                        || excludedFeatureIds.contains(feature.id()) || !resolvedFeatureIds.add(feature.id())) continue;
                feature.discover();
                events.add(eventName(feature, SpatialTrigger.BECOME_VISIBLE, cell));
            }
        }
        return List.copyOf(events);
    }

    public List<String> resolveCombatTurnStart(CombatMap map) {
        List<String> events = new ArrayList<>();
        Set<UUID> triggeredFeatureIds = new HashSet<>();
        for (SpatialFeature feature : map.spatialFeatures()) {
            // A trigger belongs to the spatial feature, not to each occupied
            // cell. A multi-cell area effect therefore fires once per turn.
            feature.cells().stream().min(Comparator.comparingInt(GridPosition::x).thenComparingInt(GridPosition::y))
                    .ifPresent(cell -> map.spatialFeatures().stream()
                            .filter(candidate -> candidate.cells().contains(cell)
                                    && triggeredFeatureIds.add(candidate.id()))
                            .forEach(candidate -> events.addAll(resolveFeature(candidate,
                                    SpatialTrigger.COMBAT_TURN_START, cell))));
        }
        return List.copyOf(events);
    }

    private static List<String> resolveFeature(SpatialFeature feature, SpatialTrigger trigger, GridPosition cell) {
        if (!feature.cells().contains(cell) || !feature.triggers().contains(trigger) || !feature.canTrigger()) return List.of();
        // Hidden features are revealed only by the detection/observation success
        // paths. Entry/exit triggers may still resolve after a failed detection;
        // the failed check itself remains silent and does not reveal the feature.
        if (feature.visibility() == SpatialFeatureVisibility.HIDDEN
                && trigger != SpatialTrigger.ENTER_CELL && trigger != SpatialTrigger.LEAVE_CELL) return List.of();
        if (feature.type() == com.dndmaster.combatmap.domain.SpatialFeatureType.SECRET_DOOR
                && trigger != SpatialTrigger.INTERACT
                && feature.visibility() != SpatialFeatureVisibility.HIDDEN) return List.of();
        if (feature.type() == com.dndmaster.combatmap.domain.SpatialFeatureType.SECRET_DOOR) {
            if (trigger == SpatialTrigger.INTERACT) feature.open();
            else return List.of(eventName(feature, trigger, cell));
        } else {
            feature.trigger();
        }
        return List.of(eventName(feature, trigger, cell));
    }

    private static String eventName(SpatialFeature feature, SpatialTrigger trigger, GridPosition cell) {
        String action = trigger == SpatialTrigger.INTERACT && feature.type() == com.dndmaster.combatmap.domain.SpatialFeatureType.SECRET_DOOR
                ? "OPENED" : trigger == SpatialTrigger.INTERACT ? "INTERACTED" : "TRIGGERED";
        if (trigger != SpatialTrigger.INTERACT && (trigger == SpatialTrigger.BECOME_VISIBLE
                || feature.visibility() == SpatialFeatureVisibility.DISCOVERED)) action = "DISCOVERED";
        return feature.type().name() + "_" + action + ":" + cell.x() + "," + cell.y();
    }
}
