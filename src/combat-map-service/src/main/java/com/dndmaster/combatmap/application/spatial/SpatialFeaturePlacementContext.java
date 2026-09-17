package com.dndmaster.combatmap.application.spatial;

import com.dndmaster.combatmap.domain.CombatMap;
import java.util.List;
import java.util.Objects;

/** 준비 전용 입력이다. 플레이어 projection이나 저장 권한을 포함하지 않는다. */
public record SpatialFeaturePlacementContext(CombatMap map, String storyPlanReference,
        int attempt, List<String> previousFailureReasons,
        List<SpatialFeaturePreparationInput.Requirement> requirements) {
    public SpatialFeaturePlacementContext {
        map = Objects.requireNonNull(map, "map must not be null");
        storyPlanReference = required(storyPlanReference, "story plan reference");
        if (attempt < 1 || attempt > 3) throw new IllegalArgumentException("placement attempt must be between 1 and 3");
        previousFailureReasons = List.copyOf(Objects.requireNonNull(previousFailureReasons, "failure reasons must not be null"));
        requirements = List.copyOf(Objects.requireNonNull(requirements, "spatial requirements must not be null"));
    }

    public SpatialFeaturePlacementContext(CombatMap map, String storyPlanReference,
            int attempt, List<String> previousFailureReasons) {
        this(map, storyPlanReference, attempt, previousFailureReasons, List.of());
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value.trim();
    }
}
