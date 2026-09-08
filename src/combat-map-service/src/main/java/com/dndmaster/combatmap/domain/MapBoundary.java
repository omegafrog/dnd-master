package com.dndmaster.combatmap.domain;

import java.util.Locale;
import java.util.Objects;

/** A shared side between two game cells, or a side on the outside of the map. */
public record MapBoundary(int x, int y, Orientation orientation, Kind kind) {
    public enum Orientation { HORIZONTAL, VERTICAL }
    public enum Kind { WALL, DOOR }

    public MapBoundary { Objects.requireNonNull(orientation); Objects.requireNonNull(kind); }

    public static MapBoundary parse(String value) {
        String[] parts = value == null ? new String[0] : value.split(",");
        if (parts.length != 4) throw new IllegalArgumentException("map boundary must be x,y,orientation,kind");
        try {
            return new MapBoundary(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]),
                    Orientation.valueOf(parts[2].trim().toUpperCase(Locale.ROOT)),
                    Kind.valueOf(parts[3].trim().toUpperCase(Locale.ROOT)));
        } catch (RuntimeException exception) { throw new IllegalArgumentException("invalid map boundary", exception); }
    }

    public String encoded() { return x + "," + y + "," + orientation + "," + kind; }

    public boolean inside(GridSpec grid) {
        return orientation == Orientation.HORIZONTAL
                ? x >= 0 && x < grid.width() && y >= 0 && y <= grid.height()
                : x >= 0 && x <= grid.width() && y >= 0 && y < grid.height();
    }

    public boolean blocks(GridPosition from, GridPosition to) {
        if (kind != Kind.WALL && kind != Kind.DOOR) return false;
        int dx = to.x() - from.x(); int dy = to.y() - from.y();
        if (Math.abs(dx) > 1 || Math.abs(dy) > 1 || (dx == 0 && dy == 0)) return false;
        boolean horizontalCrossing = orientation == Orientation.HORIZONTAL && ((dy > 0 && x == from.x() && y == from.y() + 1)
                || (dy < 0 && x == from.x() && y == from.y()));
        boolean verticalCrossing = orientation == Orientation.VERTICAL && ((dx > 0 && x == from.x() + 1 && y == from.y())
                || (dx < 0 && x == from.x() && y == from.y()));
        return horizontalCrossing || verticalCrossing;
    }
}
