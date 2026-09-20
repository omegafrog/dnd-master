package com.dndmaster.combatmap.domain;

import java.util.Collection;
import java.util.Objects;
import java.util.Set;

/** 공통 시선 계산 경계. 공개 투영과 공간 요소 탐지는 같은 geometry를 사용한다. */
public final class LineOfSightQuery {
    public boolean clear(GridPosition origin, GridPosition target, Set<GridPosition> blockers,
            Collection<MapBoundary> boundaries) {
        Objects.requireNonNull(origin, "line-of-sight origin must not be null");
        Objects.requireNonNull(target, "line-of-sight target must not be null");
        Objects.requireNonNull(blockers, "line-of-sight blockers must not be null");
        Objects.requireNonNull(boundaries, "line-of-sight boundaries must not be null");
        int dx = Math.abs(target.x() - origin.x());
        int dy = Math.abs(target.y() - origin.y());
        int sx = Integer.compare(target.x(), origin.x());
        int sy = Integer.compare(target.y(), origin.y());
        int x = origin.x();
        int y = origin.y();
        int error = dx - dy;
        while (x != target.x() || y != target.y()) {
            int twice = error * 2;
            boolean stepX = twice > -dy;
            boolean stepY = twice < dx;
            if (stepX) {
                error -= dy;
                x += sx;
            }
            if (stepY) {
                error += dx;
                y += sy;
            }
            GridPosition step = new GridPosition(x, y);
            GridPosition previous = new GridPosition(x - (stepX ? sx : 0), y - (stepY ? sy : 0));
            if (boundaries.stream().anyMatch(boundary -> boundary.blocks(previous, step))) return false;
            if (!step.equals(target) && blockers.contains(step)) return false;
        }
        return true;
    }
}
