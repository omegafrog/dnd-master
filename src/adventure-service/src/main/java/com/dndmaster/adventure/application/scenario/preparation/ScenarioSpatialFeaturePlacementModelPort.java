package com.dndmaster.adventure.application.scenario.preparation;

import com.dndmaster.adventure.domain.scenario.MapDefinition;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Scenario Preparation's candidate-only dependency on AI Game Master. */
public interface ScenarioSpatialFeaturePlacementModelPort {
    Proposal propose(Context context);

    record Context(MapDefinition map, int attempt, List<String> previousFailureReasons) {
        public Context {
            map = Objects.requireNonNull(map, "map must not be null");
            if (attempt < 1 || attempt > 3) throw new IllegalArgumentException("placement attempt must be between 1 and 3");
            previousFailureReasons = List.copyOf(Objects.requireNonNull(previousFailureReasons));
        }
    }

    record Candidate(UUID featureId, String type, List<String> cells, boolean required) {
        public Candidate {
            featureId = Objects.requireNonNull(featureId, "feature id must not be null");
            type = ScenarioSpatialFeaturePlacementModelPort.required(type, "feature type");
            cells = List.copyOf(Objects.requireNonNull(cells, "cells must not be null"));
        }
    }

    record Proposal(List<Candidate> candidates) {
        public Proposal { candidates = List.copyOf(Objects.requireNonNull(candidates)); }
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value.trim();
    }
}
