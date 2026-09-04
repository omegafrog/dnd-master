package com.dndmaster.adventure.domain.runtime.narrative;

public record WorldFact(String id, String value, boolean mutable, FactAuthority authority) {
    public WorldFact(String id, String value, boolean mutable) {
        this(id, value, mutable, FactAuthority.GENERATED_UNEXPOSED);
    }

    public WorldFact {
        id = required(id, "world fact id");
        value = required(value, "world fact value");
        authority = authority == null ? FactAuthority.GENERATED_UNEXPOSED : authority;
    }
    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value.trim();
    }
}
