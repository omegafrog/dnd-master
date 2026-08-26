package com.dndmaster.adventure.domain.adventure;

import java.util.Objects;

public record TacticalEnvironment(String id, String kind, NormalizedCoordinate coordinate, PlacementGrounding grounding) {
    public TacticalEnvironment {
        id = required(id, "environment id");
        kind = required(kind, "environment kind");
        coordinate = Objects.requireNonNull(coordinate, "environment coordinate must not be null");
        grounding = Objects.requireNonNull(grounding, "environment grounding must not be null");
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value.trim();
    }
}
