package com.dndmaster.combatmap.domain;
public record GridSpec(int width, int height, int cellSize, int distanceUnit) {
    public GridSpec {
        if (width <= 0 || height <= 0 || cellSize <= 0 || distanceUnit <= 0) throw new IllegalArgumentException("grid values must be positive");
    }
    public boolean contains(GridPosition position) { return position.x() < width && position.y() < height; }
}
