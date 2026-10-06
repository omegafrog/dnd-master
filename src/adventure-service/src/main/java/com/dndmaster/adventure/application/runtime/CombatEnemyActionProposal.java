package com.dndmaster.adventure.application.runtime;

import java.util.List;

/** One rules-backed action proposed for a prepared enemy profile. */
public record CombatEnemyActionProposal(String name, String description, List<String> citationKeys) {
    public CombatEnemyActionProposal {
        if (name == null || name.isBlank() || description == null || description.isBlank()) {
            throw new IllegalArgumentException("enemy action name and description are required");
        }
        name = name.trim();
        description = description.trim();
        citationKeys = List.copyOf(citationKeys == null ? List.of() : citationKeys);
        if (citationKeys.isEmpty() || citationKeys.stream().anyMatch(key -> key == null || key.isBlank())) {
            throw new IllegalArgumentException("enemy action requires source citations");
        }
    }
}
