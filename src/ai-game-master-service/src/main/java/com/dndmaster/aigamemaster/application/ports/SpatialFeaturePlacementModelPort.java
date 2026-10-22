package com.dndmaster.aigamemaster.application.ports;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Scenario Preparation 전용 공간 요소 후보 계약. 정본 지도 저장 권한은 포함하지 않는다. */
public interface SpatialFeaturePlacementModelPort {
    PlacementOutput propose(PlacementInput input);

    record PlacementInput(UUID soloPlayerId, String scenarioPackageVersion, int attempt, List<String> previousFailureReasons,
            int gridWidth, int gridHeight, List<String> obstacles, List<Requirement> requirements,
            List<EncounterRequirement> encounters, String mapLayoutContext, String imageDataUri) {
        public PlacementInput(UUID soloPlayerId, String scenarioPackageVersion, int attempt, List<String> previousFailureReasons,
                int gridWidth, int gridHeight, List<String> obstacles, List<Requirement> requirements) {
            this(soloPlayerId, scenarioPackageVersion, attempt, previousFailureReasons, gridWidth, gridHeight,
                    obstacles, requirements, List.of(), "", "");
        }
        public PlacementInput(UUID soloPlayerId, String scenarioPackageVersion, int attempt, List<String> previousFailureReasons,
                int gridWidth, int gridHeight, List<String> obstacles, List<Requirement> requirements,
                List<EncounterRequirement> encounters) {
            this(soloPlayerId, scenarioPackageVersion, attempt, previousFailureReasons, gridWidth, gridHeight,
                    obstacles, requirements, encounters, "", "");
        }
        public PlacementInput {
            soloPlayerId = Objects.requireNonNull(soloPlayerId, "solo player id must not be null");
            scenarioPackageVersion = SpatialFeaturePlacementModelPort.required(scenarioPackageVersion, "scenario package version");
            if (attempt < 1 || attempt > 3) throw new IllegalArgumentException("placement attempt must be between 1 and 3");
            previousFailureReasons = List.copyOf(Objects.requireNonNull(previousFailureReasons));
            if (gridWidth < 1 || gridHeight < 1) throw new IllegalArgumentException("grid dimensions must be positive");
            obstacles = List.copyOf(Objects.requireNonNull(obstacles));
            requirements = List.copyOf(Objects.requireNonNull(requirements));
            encounters = List.copyOf(Objects.requireNonNull(encounters));
            mapLayoutContext = mapLayoutContext == null ? "" : mapLayoutContext.trim();
            imageDataUri = imageDataUri == null ? "" : imageDataUri.trim();
            if (!imageDataUri.isBlank() && !imageDataUri.startsWith("data:image/")) throw new IllegalArgumentException("map image must be a data image URI");
        }
    }

    record EncounterRequirement(String scenarioId, String enemyKey, String displayName, int count,
            List<String> sourceReferences, List<String> sourceExcerpts) {
        public EncounterRequirement(String scenarioId, String enemyKey, String displayName, int count,
                List<String> sourceReferences) { this(scenarioId, enemyKey, displayName, count, sourceReferences, List.of()); }
        public EncounterRequirement {
            scenarioId = required(scenarioId, "scenario id");
            enemyKey = required(enemyKey, "enemy key");
            displayName = required(displayName, "enemy name");
            if (count < 1) throw new IllegalArgumentException("enemy count must be positive");
            sourceReferences = List.copyOf(Objects.requireNonNull(sourceReferences));
            sourceExcerpts = List.copyOf(Objects.requireNonNull(sourceExcerpts));
        }
    }

    record EnemyCandidate(String scenarioId, List<Position> positions) {
        public EnemyCandidate { scenarioId = required(scenarioId, "scenario id"); positions = List.copyOf(Objects.requireNonNull(positions)); }
    }
    record Position(double x, double y) {
        public Position { if (!Double.isFinite(x) || !Double.isFinite(y) || x < 0 || x > 1 || y < 0 || y > 1) throw new IllegalArgumentException("position must be normalized"); }
    }

    record Requirement(UUID featureId, String type, boolean required, List<String> evidenceReferences,
            List<String> authoritativeCells) {
        public Requirement(UUID featureId, String type, boolean required, List<String> evidenceReferences) {
            this(featureId, type, required, evidenceReferences, List.of());
        }

        public Requirement {
            featureId = Objects.requireNonNull(featureId);
            type = SpatialFeaturePlacementModelPort.required(type, "feature type");
            evidenceReferences = List.copyOf(Objects.requireNonNull(evidenceReferences));
            authoritativeCells = List.copyOf(Objects.requireNonNull(authoritativeCells));
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

    record PlacementOutput(List<PlacementCandidate> candidates, List<EnemyCandidate> enemies) {
        public PlacementOutput(List<PlacementCandidate> candidates) { this(candidates, List.of()); }
        public PlacementOutput { candidates = List.copyOf(Objects.requireNonNull(candidates)); enemies = List.copyOf(Objects.requireNonNull(enemies)); }
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
        return value.trim();
    }
}
