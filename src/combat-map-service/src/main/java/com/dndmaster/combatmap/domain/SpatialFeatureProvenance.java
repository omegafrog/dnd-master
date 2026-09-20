package com.dndmaster.combatmap.domain;

import java.util.Objects;

public record SpatialFeatureProvenance(SpatialFeatureOrigin origin, String sourceReference,
        long createdTurn, long createdMapVersion) {
    public SpatialFeatureProvenance {
        origin = Objects.requireNonNull(origin, "feature origin must not be null");
        sourceReference = sourceReference == null ? "" : sourceReference.trim();
        if (createdTurn < 0 || createdMapVersion < 0) throw new IllegalArgumentException("feature provenance versions must not be negative");
        if (origin == SpatialFeatureOrigin.STORY_PLAN && sourceReference.isBlank()) {
            throw new IllegalArgumentException("story-plan feature requires a source reference");
        }
    }

    public static SpatialFeatureProvenance storyPlan(String sourceReference, long createdTurn, long createdMapVersion) {
        return new SpatialFeatureProvenance(SpatialFeatureOrigin.STORY_PLAN, sourceReference, createdTurn, createdMapVersion);
    }

    public static SpatialFeatureProvenance runtime(String sourceReference, long createdTurn, long createdMapVersion) {
        return new SpatialFeatureProvenance(SpatialFeatureOrigin.GM_RUNTIME, sourceReference, createdTurn, createdMapVersion);
    }

    public static SpatialFeatureProvenance system(String sourceReference, long createdTurn, long createdMapVersion) {
        return new SpatialFeatureProvenance(SpatialFeatureOrigin.SYSTEM, sourceReference, createdTurn, createdMapVersion);
    }
}
