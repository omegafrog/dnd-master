package com.dndmaster.adventure.application.combat;

import java.util.Objects;

/** Confirmed player-visible combat material. Adventure Runtime composes the GM input. */
public record CombatNarrationRequest(CombatActionCommand command, String playerInput, long encounterVersion,
                                     Integer diceTotal, String judgment) {
    public CombatNarrationRequest {
        command = Objects.requireNonNull(command, "combat command must not be null");
        if (playerInput == null || playerInput.isBlank()) throw new IllegalArgumentException("player input must not be blank");
        playerInput = playerInput.trim();
        if (encounterVersion < 1) throw new IllegalArgumentException("encounter version must be positive");
        judgment = judgment == null ? "" : judgment.trim();
    }

    public static CombatNarrationRequest postResolution(CombatActionCommand command, long encounterVersion,
            Integer diceTotal, String judgment, String playerInput) {
        return new CombatNarrationRequest(command, playerInput, encounterVersion, diceTotal, judgment);
    }
}
