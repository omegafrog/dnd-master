package com.dndmaster.combatmap.application.spatial;

import com.dndmaster.combatmap.domain.DetectionSpec;
import com.dndmaster.combatmap.domain.GridPosition;
import com.dndmaster.combatmap.domain.SpatialFeatureType;
import com.dndmaster.combatmap.domain.SpatialTrigger;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * The only spatial-preparation payload accepted by Combat Map.
 * Scenario Preparation has already called and validated the placement model
 * before constructing this batch.
 */
public record SpatialFeaturePlacementBatch(String scenarioPackageVersion, boolean blocked,
        List<Placement> placements, List<String> warnings, List<String> failures) {
    public SpatialFeaturePlacementBatch {
        scenarioPackageVersion = required(scenarioPackageVersion, "scenario package version");
        placements = List.copyOf(Objects.requireNonNull(placements, "placements must not be null"));
        warnings = List.copyOf(Objects.requireNonNull(warnings, "warnings must not be null"));
        failures = List.copyOf(Objects.requireNonNull(failures, "failures must not be null"));
        if (blocked && !placements.isEmpty()) {
            throw new IllegalArgumentException("blocked preparation cannot contain placements");
        }
    }

    public record Placement(UUID featureId, SpatialFeatureType type, boolean required,
            List<GridPosition> cells, Evidence evidence, DetectionSpec detectionSpec,
            Set<SpatialTrigger> triggers, int durationTurns, String removalPolicy,
            boolean overlapAllowed) {
        public Placement {
            featureId = Objects.requireNonNull(featureId, "feature id must not be null");
            type = Objects.requireNonNull(type, "feature type must not be null");
            cells = List.copyOf(Objects.requireNonNull(cells, "feature cells must not be null"));
            evidence = Objects.requireNonNull(evidence, "placement evidence must not be null");
            triggers = Set.copyOf(Objects.requireNonNull(triggers, "feature triggers must not be null"));
            if (durationTurns < -1) throw new IllegalArgumentException("duration must be -1 or non-negative");
            removalPolicy = removalPolicy == null ? "" : removalPolicy.trim();
        }
    }

    /** Structured evidence; no opaque evidence string is authoritative here. */
    public record Evidence(UUID sourceDocumentId, long sourceExtractionVersion, String sourceLocator,
            String resolutionUnitId, String scenarioPackageVersion, Set<GridPosition> allowedCells) {
        public Evidence {
            sourceDocumentId = Objects.requireNonNull(sourceDocumentId, "source document id must not be null");
            if (sourceExtractionVersion <= 0) throw new IllegalArgumentException("source extraction version must be positive");
            sourceLocator = required(sourceLocator, "source locator");
            resolutionUnitId = required(resolutionUnitId, "resolution unit id");
            scenarioPackageVersion = required(scenarioPackageVersion, "scenario package version");
            allowedCells = Set.copyOf(Objects.requireNonNull(allowedCells, "allowed cells must not be null"));
            if (allowedCells.isEmpty()) throw new IllegalArgumentException("allowed cells must not be empty");
        }
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value.trim();
    }
}
