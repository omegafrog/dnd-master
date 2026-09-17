package com.dndmaster.aigamemaster.application.ports;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Scenario Preparation 전용 공간 요소 후보 계약. 정본 지도 저장 권한은 포함하지 않는다. */
public interface SpatialFeaturePlacementModelPort {
    PlacementOutput propose(PlacementInput input);

    record PlacementInput(String storyPlanReference, int attempt, List<String> previousFailureReasons,
            int gridWidth, int gridHeight, List<String> obstacles, List<Requirement> requirements) {
        public PlacementInput {
            storyPlanReference = SpatialFeaturePlacementModelPort.required(storyPlanReference, "story plan reference");
            if (attempt < 1 || attempt > 3) throw new IllegalArgumentException("placement attempt must be between 1 and 3");
            previousFailureReasons = List.copyOf(Objects.requireNonNull(previousFailureReasons));
            if (gridWidth < 1 || gridHeight < 1) throw new IllegalArgumentException("grid dimensions must be positive");
            obstacles = List.copyOf(Objects.requireNonNull(obstacles));
            requirements = List.copyOf(Objects.requireNonNull(requirements));
        }
    }

    record Requirement(UUID featureId, String type, boolean required, List<String> evidenceReferences) {
        public Requirement {
            featureId = Objects.requireNonNull(featureId);
            type = SpatialFeaturePlacementModelPort.required(type, "feature type");
            evidenceReferences = List.copyOf(Objects.requireNonNull(evidenceReferences));
        }
    }

    record PlacementCandidate(UUID featureId, String type, List<String> cells, boolean required, String evidenceReference) {
        public PlacementCandidate {
            featureId = Objects.requireNonNull(featureId);
            type = SpatialFeaturePlacementModelPort.required(type, "feature type");
            cells = List.copyOf(Objects.requireNonNull(cells));
            evidenceReference = evidenceReference == null ? "" : evidenceReference.trim();
        }
    }

    record PlacementOutput(List<PlacementCandidate> candidates) {
        public PlacementOutput { candidates = List.copyOf(Objects.requireNonNull(candidates)); }
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
        return value.trim();
    }
}
