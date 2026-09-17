package com.dndmaster.combatmap.application.spatial;

import com.dndmaster.combatmap.domain.DetectionSpec;
import com.dndmaster.combatmap.domain.GridPosition;
import com.dndmaster.combatmap.domain.SpatialFeatureType;
import com.dndmaster.combatmap.domain.SpatialTrigger;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Scenario Preparation이 잠근 공간 요소 요구사항이다. AI 제안은 이 입력을 바꿀 수 없다. */
public record SpatialFeaturePreparationInput(String storyPlanReference, List<Requirement> requirements) {
    public SpatialFeaturePreparationInput {
        storyPlanReference = required(storyPlanReference, "story plan reference");
        requirements = List.copyOf(Objects.requireNonNull(requirements, "spatial requirements must not be null"));
        Set<UUID> ids = new java.util.HashSet<>();
        for (Requirement requirement : requirements) {
            if (!ids.add(requirement.featureId())) throw new IllegalArgumentException("spatial requirement ids must be unique");
        }
    }

    public static SpatialFeaturePreparationInput empty(String storyPlanReference) {
        return new SpatialFeaturePreparationInput(storyPlanReference, List.of());
    }

    public record Requirement(UUID featureId, SpatialFeatureType type, boolean required,
            Set<String> evidenceReferences, Set<GridPosition> authoritativeCells,
            DetectionSpec detectionSpec, Set<SpatialTrigger> triggers) {
        public Requirement(UUID featureId, SpatialFeatureType type, boolean required,
                Set<String> evidenceReferences, DetectionSpec detectionSpec, Set<SpatialTrigger> triggers) {
            this(featureId, type, required, evidenceReferences, Set.of(), detectionSpec, triggers);
        }

        public Requirement {
            featureId = Objects.requireNonNull(featureId, "spatial requirement id must not be null");
            type = Objects.requireNonNull(type, "spatial requirement type must not be null");
            evidenceReferences = Set.copyOf(Objects.requireNonNull(evidenceReferences, "evidence references must not be null"));
            if (evidenceReferences.stream().anyMatch(value -> value == null || value.isBlank())) {
                throw new IllegalArgumentException("evidence references must not be blank");
            }
            authoritativeCells = Set.copyOf(Objects.requireNonNull(authoritativeCells, "authoritative cells must not be null"));
            triggers = Set.copyOf(Objects.requireNonNull(triggers, "spatial requirement triggers must not be null"));
        }
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value.trim();
    }
}
