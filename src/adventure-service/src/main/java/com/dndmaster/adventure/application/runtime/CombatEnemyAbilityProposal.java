package com.dndmaster.adventure.application.runtime;

import java.util.List;

/** A single ability score and its supporting rule-source citation. */
public record CombatEnemyAbilityProposal(String ability, int score, List<String> citationKeys) {
    public CombatEnemyAbilityProposal {
        if (ability == null || ability.isBlank() || score < 1 || score > 30) {
            throw new IllegalArgumentException("enemy ability and score are invalid");
        }
        ability = ability.trim().toUpperCase(java.util.Locale.ROOT);
        citationKeys = List.copyOf(citationKeys == null ? List.of() : citationKeys);
        if (citationKeys.isEmpty() || citationKeys.stream().anyMatch(key -> key == null || key.isBlank())) {
            throw new IllegalArgumentException("enemy ability requires source citations");
        }
    }
}
