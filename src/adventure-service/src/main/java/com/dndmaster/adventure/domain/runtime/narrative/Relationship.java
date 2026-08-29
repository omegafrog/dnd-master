package com.dndmaster.adventure.domain.runtime.narrative;

public record Relationship(String fromActorId, String toActorId, String attitude, int trust) {
    public Relationship {
        fromActorId = required(fromActorId, "relationship source"); toActorId = required(toActorId, "relationship target");
        attitude = required(attitude, "relationship attitude");
        if (trust < -100 || trust > 100) throw new IllegalArgumentException("relationship trust must be between -100 and 100");
    }
    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value.trim();
    }
}
