package com.dndmaster.adventure.application.scenario.preparation;

import com.dndmaster.adventure.domain.scenario.MapDefinition;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Candidate validation result owned by Scenario Preparation. */
public record ScenarioSpatialFeaturePreparationResult(boolean activationAllowed,
        List<Placement> placements, List<String> warnings, List<String> failures, int attempts) {
    public ScenarioSpatialFeaturePreparationResult {
        placements = List.copyOf(Objects.requireNonNull(placements));
        warnings = List.copyOf(Objects.requireNonNull(warnings));
        failures = List.copyOf(Objects.requireNonNull(failures));
        if (attempts < 0 || attempts > 3) throw new IllegalArgumentException("invalid preparation attempts");
    }

    public record Placement(UUID featureId, String type, boolean required, List<String> cells,
            Evidence evidence, String detectionRuleReference, Integer detectionDifficulty,
            String detectionMode, List<String> triggers, int durationTurns,
            String removalPolicy, boolean overlapAllowed) {
        public Placement {
            featureId = Objects.requireNonNull(featureId);
            type = ScenarioSpatialFeaturePreparationResult.required(type, "feature type");
            cells = List.copyOf(Objects.requireNonNull(cells));
            evidence = Objects.requireNonNull(evidence);
            triggers = List.copyOf(triggers == null ? List.of() : triggers);
            removalPolicy = removalPolicy == null ? "" : removalPolicy.trim();
        }
    }

    /** Structured evidence sent across the Scenario Preparation → Combat Map boundary. */
    public record Evidence(UUID sourceDocumentId, long sourceExtractionVersion, String sourceLocator,
            String resolutionUnitId, String scenarioPackageVersion, List<String> allowedCells) {
        public Evidence {
            sourceDocumentId = Objects.requireNonNull(sourceDocumentId);
            if (sourceExtractionVersion <= 0) throw new IllegalArgumentException("source extraction version must be positive");
            sourceLocator = required(sourceLocator, "source locator");
            resolutionUnitId = required(resolutionUnitId, "resolution unit id");
            scenarioPackageVersion = required(scenarioPackageVersion, "scenario package version");
            allowedCells = List.copyOf(Objects.requireNonNull(allowedCells));
            if (allowedCells.isEmpty()) throw new IllegalArgumentException("allowed cells must not be empty");
        }
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value.trim();
    }
}
