package com.dndmaster.adventure.domain.adventure;

import java.util.Objects;

public record TacticalPlacement(String id, TacticalPlacementKind kind, NormalizedCoordinate coordinate,
        PlacementGrounding grounding) {
    public TacticalPlacement {
        id = required(id, "placement id");
        kind = Objects.requireNonNull(kind, "placement kind must not be null");
        coordinate = Objects.requireNonNull(coordinate, "placement coordinate must not be null");
        grounding = Objects.requireNonNull(grounding, "placement grounding must not be null");
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value.trim();
    }
}
