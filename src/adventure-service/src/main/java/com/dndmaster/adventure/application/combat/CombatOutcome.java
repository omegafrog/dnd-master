package com.dndmaster.adventure.application.combat;

import java.util.Objects;

/** A rule-adjudicated result and optional structured sheet effect. */
public record CombatOutcome(String judgment, CombatCharacterMutation mutation, boolean combatEnded) {
    public CombatOutcome {
        if (judgment == null || judgment.isBlank()) throw new IllegalArgumentException("judgment must not be blank");
        judgment = judgment.trim();
        mutation = Objects.requireNonNull(mutation, "mutation must not be null");
    }

    public CombatOutcome(String judgment) {
        this(judgment, CombatCharacterMutation.none(), false);
    }

    public CombatOutcome(String judgment, CombatCharacterMutation mutation) {
        this(judgment, mutation, false);
    }
}
