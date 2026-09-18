package com.dndmaster.combatmap.application.spatial;

import com.dndmaster.combatmap.domain.CombatMap;
import com.dndmaster.combatmap.domain.GridPosition;
import com.dndmaster.combatmap.domain.SpatialFeature;
import com.dndmaster.combatmap.domain.SpatialFeatureVisibility;
import com.dndmaster.combatmap.domain.SpatialTrigger;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** 허용된 공간 발동만 적용하고 공개 결과에서는 내부 식별자를 제거한다. */
public final class SpatialTriggerResolver {
    public List<String> resolve(CombatMap map, SpatialTrigger trigger, GridPosition cell) {
        Objects.requireNonNull(map, "combat map must not be null");
        Objects.requireNonNull(trigger, "spatial trigger must not be null");
        Objects.requireNonNull(cell, "trigger cell must not be null");
        List<String> events = new ArrayList<>();
        for (SpatialFeature feature : map.spatialFeatures()) {
            if (!feature.cells().contains(cell) || !feature.triggers().contains(trigger) || !feature.canTrigger()) continue;
            if (feature.type() == com.dndmaster.combatmap.domain.SpatialFeatureType.SECRET_DOOR
                    && trigger != SpatialTrigger.INTERACT
                    && feature.visibility() != SpatialFeatureVisibility.HIDDEN) continue;
            if (feature.visibility() == SpatialFeatureVisibility.HIDDEN) feature.discover();
            if (feature.type() == com.dndmaster.combatmap.domain.SpatialFeatureType.SECRET_DOOR) {
                if (trigger == SpatialTrigger.INTERACT) feature.open();
                else {
                    events.add(eventName(feature, trigger, cell));
                    continue;
                }
            } else {
                feature.trigger();
            }
            events.add(eventName(feature, trigger, cell));
        }
        return List.copyOf(events);
    }

    public List<String> resolveCombatTurnStart(CombatMap map) {
        List<String> events = new ArrayList<>();
        for (SpatialFeature feature : map.spatialFeatures()) {
            for (GridPosition cell : feature.cells()) {
                events.addAll(resolve(map, SpatialTrigger.COMBAT_TURN_START, cell));
            }
        }
        return List.copyOf(events);
    }

    private static String eventName(SpatialFeature feature, SpatialTrigger trigger, GridPosition cell) {
        String action = trigger == SpatialTrigger.INTERACT && feature.type() == com.dndmaster.combatmap.domain.SpatialFeatureType.SECRET_DOOR
                ? "OPENED" : trigger == SpatialTrigger.INTERACT ? "INTERACTED" : "TRIGGERED";
        if (trigger != SpatialTrigger.INTERACT && (trigger == SpatialTrigger.BECOME_VISIBLE
                || feature.visibility() == SpatialFeatureVisibility.DISCOVERED)) action = "DISCOVERED";
        return feature.type().name() + "_" + action + ":" + cell.x() + "," + cell.y();
    }
}
