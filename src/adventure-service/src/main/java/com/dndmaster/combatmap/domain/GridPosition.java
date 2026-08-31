package com.dndmaster.combatmap.domain;
public record GridPosition(int x, int y) {
    public GridPosition { if (x < 0 || y < 0) throw new IllegalArgumentException("grid coordinates must not be negative"); }
    public boolean adjacentTo(GridPosition other) {
        int dx = Math.abs(x - other.x); int dy = Math.abs(y - other.y);
        return dx <= 1 && dy <= 1 && dx + dy > 0;
    }
}
