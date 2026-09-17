package com.dndmaster.adventure.domain.scenario;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Immutable, source-pinned tactical map definition compiled from a bundle. */
public record MapDefinition(
        UUID id,
        String assetId,
        String assetLocator,
        MapGrid grid,
        List<String> walls,
        List<String> doors,
        List<String> obstacles,
        MapSourceReference source,
        double confidence,
        MapSafetyStatus safetyStatus,
        List<SpatialFeatureRequirement> spatialFeatures) {
    public MapDefinition(UUID id, String assetId, String assetLocator, MapGrid grid,
            List<String> walls, List<String> doors, List<String> obstacles, MapSourceReference source,
            double confidence, MapSafetyStatus safetyStatus) {
        this(id, assetId, assetLocator, grid, walls, doors, obstacles, source, confidence, safetyStatus, List.of());
    }

    public MapDefinition {
        id = Objects.requireNonNull(id, "map definition id must not be null");
        assetId = required(assetId, "asset id");
        assetLocator = required(assetLocator, "asset locator");
        grid = Objects.requireNonNull(grid, "grid must not be null");
        walls = immutable(walls, "walls");
        doors = immutable(doors, "doors");
        obstacles = immutable(obstacles, "obstacles");
        source = Objects.requireNonNull(source, "source must not be null");
        if (confidence < 0 || confidence > 1) throw new IllegalArgumentException("confidence must be between 0 and 1");
        safetyStatus = Objects.requireNonNull(safetyStatus, "safety status must not be null");
        spatialFeatures = spatialFeatures == null ? List.of() : List.copyOf(spatialFeatures);
    }
    private static String required(String value, String name) { if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank"); return value.trim(); }
    private static List<String> immutable(List<String> value, String name) { return List.copyOf(Objects.requireNonNull(value, name + " must not be null")); }
    public boolean autoActivatable() { return safetyStatus == MapSafetyStatus.SAFE && confidence >= .8; }
    public record MapGrid(double originX, double originY, double cellSize, double rotation, String distance) {
        public MapGrid { if (cellSize <= 0) throw new IllegalArgumentException("cell size must be positive"); distance = required(distance, "distance"); }
    }

    /** Story Plan이 지도와 함께 잠그는 공간 요소 요구사항이다. 좌표는 포함하지 않는다. */
    public record SpatialFeatureRequirement(UUID featureId, String type, boolean required,
            List<String> evidenceReferences, String detectionRuleReference, Integer detectionDifficulty,
            String detectionMode, List<String> triggers) {
        public SpatialFeatureRequirement {
            featureId = Objects.requireNonNull(featureId, "spatial feature id must not be null");
            type = MapDefinition.required(type, "spatial feature type");
            evidenceReferences = List.copyOf(Objects.requireNonNull(evidenceReferences, "evidence references must not be null"));
            if (evidenceReferences.isEmpty() || evidenceReferences.stream().anyMatch(value -> value == null || value.isBlank())) {
                throw new IllegalArgumentException("published evidence reference is required");
            }
            detectionRuleReference = detectionRuleReference == null ? "" : detectionRuleReference.trim();
            detectionMode = detectionMode == null ? "" : detectionMode.trim();
            triggers = triggers == null ? List.of() : List.copyOf(triggers);
        }
    }
}
