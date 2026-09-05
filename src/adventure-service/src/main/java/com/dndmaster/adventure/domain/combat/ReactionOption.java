package com.dndmaster.adventure.domain.combat;

public record ReactionOption(String id, String label) {
    public ReactionOption {
        if (id == null || id.isBlank() || label == null || label.isBlank()) {
            throw new IllegalArgumentException("reaction option is required");
        }
    }
}
