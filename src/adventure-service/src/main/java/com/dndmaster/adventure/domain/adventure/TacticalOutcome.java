package com.dndmaster.adventure.domain.adventure;

import java.util.Objects;

public record TacticalOutcome(String id, String condition, PlacementGrounding grounding) {
    public TacticalOutcome {
        id = required(id, "outcome id");
        condition = required(condition, "outcome condition");
        grounding = Objects.requireNonNull(grounding, "outcome grounding must not be null");
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value.trim();
    }
}
