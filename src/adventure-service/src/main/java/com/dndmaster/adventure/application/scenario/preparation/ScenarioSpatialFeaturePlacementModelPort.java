package com.dndmaster.adventure.application.scenario.preparation;

import com.dndmaster.adventure.domain.scenario.MapDefinition;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Scenario Preparation's candidate-only dependency on AI Game Master. */
public interface ScenarioSpatialFeaturePlacementModelPort {
    Proposal propose(Context context);

    record Context(UUID soloPlayerId, MapDefinition map, int attempt, List<String> previousFailureReasons,
            List<EncounterRequirement> encounters) {
        public Context(UUID soloPlayerId, MapDefinition map, int attempt, List<String> previousFailureReasons) {
            this(soloPlayerId, map, attempt, previousFailureReasons, List.of());
        }
        public Context {
            soloPlayerId = Objects.requireNonNull(soloPlayerId, "solo player id must not be null");
            map = Objects.requireNonNull(map, "map must not be null");
            if (attempt < 1 || attempt > 3) throw new IllegalArgumentException("placement attempt must be between 1 and 3");
            previousFailureReasons = List.copyOf(Objects.requireNonNull(previousFailureReasons));
            encounters = List.copyOf(Objects.requireNonNull(encounters));
        }
    }

    record EncounterRequirement(String scenarioId, String enemyKey, String displayName, int count,
            boolean required, List<String> sourceReferences, List<String> sourceExcerpts) {
        public EncounterRequirement(String scenarioId, String enemyKey, String displayName, int count,
                boolean required, List<String> sourceReferences) {
            this(scenarioId, enemyKey, displayName, count, required, sourceReferences, List.of());
        }
        public EncounterRequirement {
            scenarioId = ScenarioSpatialFeaturePlacementModelPort.required(scenarioId, "scenario id");
            enemyKey = ScenarioSpatialFeaturePlacementModelPort.required(enemyKey, "enemy key");
            displayName = ScenarioSpatialFeaturePlacementModelPort.required(displayName, "enemy name");
            if (count < 1) throw new IllegalArgumentException("enemy count must be positive");
            sourceReferences = List.copyOf(Objects.requireNonNull(sourceReferences));
            sourceExcerpts = List.copyOf(Objects.requireNonNull(sourceExcerpts));
            if (sourceReferences.isEmpty()) throw new IllegalArgumentException("encounter source references are required");
        }
    }

    record EnemyCandidate(String scenarioId, List<Position> positions) {
        public EnemyCandidate {
            scenarioId = required(scenarioId, "scenario id");
            positions = List.copyOf(Objects.requireNonNull(positions));
        }
    }

    record Position(double x, double y) {
        public Position {
            if (!Double.isFinite(x) || !Double.isFinite(y) || x < 0 || x > 1 || y < 0 || y > 1)
                throw new IllegalArgumentException("enemy position must be normalized to the map");
        }
    }

    record Candidate(UUID featureId, String type, List<String> cells, boolean required) {
        public Candidate {
            featureId = Objects.requireNonNull(featureId, "feature id must not be null");
            type = ScenarioSpatialFeaturePlacementModelPort.required(type, "feature type");
            cells = List.copyOf(Objects.requireNonNull(cells, "cells must not be null"));
        }
    }

    record Proposal(List<Candidate> candidates, List<EnemyCandidate> enemies) {
        public Proposal(List<Candidate> candidates) { this(candidates, List.of()); }
        public Proposal {
            candidates = List.copyOf(Objects.requireNonNull(candidates));
            enemies = List.copyOf(Objects.requireNonNull(enemies));
        }
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value.trim();
    }
}
