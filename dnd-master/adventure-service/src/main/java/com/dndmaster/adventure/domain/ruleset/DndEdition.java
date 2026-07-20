package com.dndmaster.adventure.domain.ruleset;

public record DndEdition(String value) {
    public DndEdition {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("D&D edition must not be blank");
        }
        value = value.trim();
    }
}
