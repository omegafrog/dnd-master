package com.dndmaster.combatmap.application.spatial;

import com.dndmaster.combatmap.domain.CombatMap;
import com.dndmaster.combatmap.domain.GridPosition;
import com.dndmaster.combatmap.domain.SpatialFeature;
import com.dndmaster.combatmap.domain.SpatialTrigger;
import java.util.List;
import java.util.Objects;

/** 관찰·상호작용·전투 턴 시작의 공간 요소 경계를 제공한다. */
public final class SpatialFeatureRuntimeService {
    private final SpatialTriggerResolver triggerResolver;

    public SpatialFeatureRuntimeService() {
        this(new SpatialTriggerResolver());
    }

    public SpatialFeatureRuntimeService(SpatialTriggerResolver triggerResolver) {
        this.triggerResolver = Objects.requireNonNull(triggerResolver);
    }

    public List<String> observe(CombatMap map, GridPosition cell) {
        return triggerResolver.resolve(map, SpatialTrigger.OBSERVE, cell);
    }

    public List<String> interact(CombatMap map, GridPosition cell) {
        return triggerResolver.resolve(map, SpatialTrigger.INTERACT, cell);
    }

    public List<String> combatTurnStart(CombatMap map) {
        return triggerResolver.resolveCombatTurnStart(map);
    }

    public void advanceDurations(CombatMap map) {
        for (SpatialFeature feature : map.spatialFeatures()) feature.advanceDuration();
    }
}
