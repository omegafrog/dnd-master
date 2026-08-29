package com.dndmaster.adventure.domain.runtime.narrative;

import java.util.Objects;

public record WorldFact(String id, String value, boolean mutable) {
    public WorldFact {
        id = required(id, "world fact id");
        value = required(value, "world fact value");
    }
    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value.trim();
    }
}
