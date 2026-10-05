package com.dndmaster.adventure.domain.combat;

public record CombatSpell(String name, int level) {
    public CombatSpell {
        if (name == null || name.isBlank() || level < 0) throw new IllegalArgumentException("spell identity is invalid");
        name = name.trim();
    }
}
