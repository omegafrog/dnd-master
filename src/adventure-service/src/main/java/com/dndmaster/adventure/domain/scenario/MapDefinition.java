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
        MapSafetyStatus safetyStatus) {
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
    }
    private static String required(String value, String name) { if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank"); return value.trim(); }
    private static List<String> immutable(List<String> value, String name) { return List.copyOf(Objects.requireNonNull(value, name + " must not be null")); }
    public boolean autoActivatable() { return safetyStatus == MapSafetyStatus.SAFE && confidence >= .8; }
    public record MapGrid(double originX, double originY, double cellSize, double rotation, String distance) {
        public MapGrid { if (cellSize <= 0) throw new IllegalArgumentException("cell size must be positive"); distance = required(distance, "distance"); }
    }
}
