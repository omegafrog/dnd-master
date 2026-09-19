package com.dndmaster.combatmap.domain;
import java.util.List; import java.util.Objects;
public record MovementPath(List<GridPosition> orderedPositions, int distance) {
    public MovementPath {
        orderedPositions = List.copyOf(Objects.requireNonNull(orderedPositions));
        if (orderedPositions.isEmpty() || orderedPositions.stream().anyMatch(Objects::isNull)) throw new IllegalArgumentException("movement path requires positions");
        if (distance < 0 || orderedPositions.size() == 1 && distance != 0) throw new IllegalArgumentException("movement distance is invalid");
    }
}
