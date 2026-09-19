package com.dndmaster.combatmap.application.spatial;

import com.dndmaster.combatmap.domain.CombatMap;
import com.dndmaster.combatmap.domain.Door;
import com.dndmaster.combatmap.domain.GridPosition;
import com.dndmaster.combatmap.domain.LineOfSightQuery;
import com.dndmaster.combatmap.domain.PlayerId;
import com.dndmaster.combatmap.domain.SpatialFeature;
import com.dndmaster.combatmap.domain.SpatialFeatureVisibility;
import com.dndmaster.combatmap.domain.TokenId;
import com.dndmaster.combatmap.domain.VisibilityProfile;
import com.dndmaster.combatmap.domain.VisibilitySnapshot;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** 현재 공개된 범위 안에서만 내부 탐지 후보를 만든다. 후보는 플레이어 DTO가 아니다. */
public final class SpatialFeatureDetectionPolicy {
    private final LineOfSightQuery lineOfSight;
    private final VisibilityProfile profile;

    public SpatialFeatureDetectionPolicy() {
        this(new LineOfSightQuery(), VisibilityProfile.DEFAULT);
    }

    public SpatialFeatureDetectionPolicy(LineOfSightQuery lineOfSight, VisibilityProfile profile) {
        this.lineOfSight = Objects.requireNonNull(lineOfSight, "line-of-sight query must not be null");
        this.profile = Objects.requireNonNull(profile, "visibility profile must not be null");
    }

    public List<SpatialFeature> candidates(CombatMap map, PlayerId playerId, TokenId tokenId) {
        Objects.requireNonNull(map, "combat map must not be null");
        GridPosition origin = map.playerTokenPosition(Objects.requireNonNull(playerId), Objects.requireNonNull(tokenId));
        VisibilitySnapshot visibility = map.visibilitySnapshot();
        if (visibility == null || !visibility.current().contains(origin)) return List.of();
        Set<GridPosition> blockers = new HashSet<>(map.obstacles());
        map.doors().stream().filter(door -> !door.open()).map(Door::position).forEach(blockers::add);
        return candidates(origin, map.spatialFeatures(), visibility.current(), blockers, map.publicBoundaries());
    }

    /** Returns only features that can be detected before entering the requested next cell. */
    public List<SpatialFeature> candidates(CombatMap map, PlayerId playerId, TokenId tokenId,
            GridPosition nextCell) {
        Objects.requireNonNull(nextCell, "next cell must not be null");
        Objects.requireNonNull(map, "combat map must not be null");
        GridPosition origin = map.playerTokenPosition(Objects.requireNonNull(playerId), Objects.requireNonNull(tokenId));
        VisibilitySnapshot visibility = map.visibilitySnapshot();
        if (visibility == null || !visibility.current().contains(origin)) return List.of();
        Set<GridPosition> blockers = new HashSet<>(map.obstacles());
        map.doors().stream().filter(door -> !door.open()).map(Door::position).forEach(blockers::add);
        return candidates(origin, map.spatialFeatures(), visibility.current(), blockers, map.publicBoundaries(), nextCell);
    }

    public List<SpatialFeature> candidates(GridPosition origin, List<SpatialFeature> features,
            Set<GridPosition> currentVisible, Set<GridPosition> blockers,
            java.util.Collection<com.dndmaster.combatmap.domain.MapBoundary> boundaries) {
        return candidates(origin, features, currentVisible, blockers, boundaries, null);
    }

    private List<SpatialFeature> candidates(GridPosition origin, List<SpatialFeature> features,
            Set<GridPosition> currentVisible, Set<GridPosition> blockers,
            java.util.Collection<com.dndmaster.combatmap.domain.MapBoundary> boundaries,
            GridPosition requestedCell) {
        Objects.requireNonNull(origin, "detection origin must not be null");
        Objects.requireNonNull(features, "spatial features must not be null");
        Objects.requireNonNull(currentVisible, "current visible cells must not be null");
        List<SpatialFeature> result = new ArrayList<>();
        for (SpatialFeature feature : features) {
            if (feature.visibility() != SpatialFeatureVisibility.HIDDEN || feature.detectionSpec() == null) continue;
            boolean candidate = requestedCell == null
                    ? feature.cells().stream().anyMatch(cell -> detectable(origin, cell, currentVisible, blockers, boundaries))
                    : feature.cells().contains(requestedCell)
                            && detectable(origin, requestedCell, currentVisible, blockers, boundaries);
            if (candidate) result.add(feature);
        }
        return result.stream().sorted(Comparator.comparing(feature -> feature.id().toString())).toList();
    }

    private boolean detectable(GridPosition origin, GridPosition cell, Set<GridPosition> currentVisible,
            Set<GridPosition> blockers, java.util.Collection<com.dndmaster.combatmap.domain.MapBoundary> boundaries) {
        return currentVisible.contains(cell)
                && Math.max(Math.abs(cell.x() - origin.x()), Math.abs(cell.y() - origin.y())) <= profile.maxRangeCells()
                && lineOfSight.clear(origin, cell, blockers, boundaries);
    }
}
