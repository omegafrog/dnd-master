package com.dndmaster.combatmap.domain;

import java.util.Objects;

/** Measures range on the map's square grid, including vertical separation in cells. */
public final class GridDistance {
    private GridDistance() { }

    public static int cellsBetween(GridPosition from, int fromElevationFeet,
            GridPosition to, int toElevationFeet, int distanceUnitFeet) {
        Objects.requireNonNull(from, "origin must not be null");
        Objects.requireNonNull(to, "target must not be null");
        if (fromElevationFeet < 0 || toElevationFeet < 0 || distanceUnitFeet <= 0) {
            throw new IllegalArgumentException("elevation must be nonnegative and grid distance must be positive");
        }
        long verticalDelta = Math.abs((long) toElevationFeet - fromElevationFeet);
        long verticalCells = (verticalDelta + distanceUnitFeet - 1L) / distanceUnitFeet;
        int horizontalCells = Math.max(Math.abs(to.x() - from.x()), Math.abs(to.y() - from.y()));
        return Math.toIntExact(Math.max(horizontalCells, verticalCells));
    }
}
