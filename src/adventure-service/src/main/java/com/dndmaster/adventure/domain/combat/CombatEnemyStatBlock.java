package com.dndmaster.adventure.domain.combat;

import java.util.Objects;

/** Rulebook-derived combat data kept server-side for deterministic adjudication. */
public record CombatEnemyStatBlock(int armorClass, int hitPointMaximum, int attackModifier,
        String damageDice, CombatStatBlockSource source) {
    public CombatEnemyStatBlock {
        if (armorClass < 1 || hitPointMaximum < 1 || attackModifier < -30 || attackModifier > 30) {
            throw new IllegalArgumentException("combat stat block values are invalid");
        }
        damageDice = damageDice == null || damageDice.isBlank() ? "" : damageDice.trim();
        source = Objects.requireNonNull(source, "combat stat block source must not be null");
    }
}
