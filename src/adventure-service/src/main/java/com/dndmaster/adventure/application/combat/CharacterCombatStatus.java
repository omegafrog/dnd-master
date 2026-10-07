package com.dndmaster.adventure.application.combat;

public record CharacterCombatStatus(int currentHitPoints, int deathSavingThrowSuccesses,
        int deathSavingThrowFailures, boolean stable, boolean dead) {
    public CharacterCombatStatus {
        if (currentHitPoints < 0 || deathSavingThrowSuccesses < 0 || deathSavingThrowSuccesses > 2
                || deathSavingThrowFailures < 0 || deathSavingThrowFailures > 3) {
            throw new IllegalArgumentException("character combat status is invalid");
        }
    }
}
