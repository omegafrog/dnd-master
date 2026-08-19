package com.dndmaster.adventure.domain.adventure;

import java.util.List;
import java.util.Objects;

public record TacticalSceneBoundary(NormalizedCoordinate minimum, NormalizedCoordinate maximum,
        List<NormalizedCoordinate> forbiddenCoordinates) {
    public TacticalSceneBoundary {
        minimum = Objects.requireNonNull(minimum, "scene minimum must not be null");
        maximum = Objects.requireNonNull(maximum, "scene maximum must not be null");
        if (minimum.x() > maximum.x() || minimum.y() > maximum.y()) {
            throw new IllegalArgumentException("scene boundary is inverted");
        }
        forbiddenCoordinates = List.copyOf(Objects.requireNonNull(forbiddenCoordinates, "forbidden coordinates must be explicit"));
        for (NormalizedCoordinate point : forbiddenCoordinates) {
            if (point.x() < minimum.x() || point.x() > maximum.x() || point.y() < minimum.y() || point.y() > maximum.y()) {
                throw new IllegalArgumentException("forbidden coordinate must be inside the scene boundary");
            }
        }
    }

    public boolean contains(NormalizedCoordinate coordinate) {
        return coordinate.x() >= minimum.x() && coordinate.x() <= maximum.x()
                && coordinate.y() >= minimum.y() && coordinate.y() <= maximum.y();
    }
}
