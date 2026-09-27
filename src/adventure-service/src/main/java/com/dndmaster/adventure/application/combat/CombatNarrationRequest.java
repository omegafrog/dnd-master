package com.dndmaster.adventure.application.combat;

import java.util.Objects;

/** Confirmed player-visible combat material. Adventure Runtime composes the GM input. */
public record CombatNarrationRequest(CombatActionCommand command, String playerInput, long encounterVersion,
                                     Integer diceTotal, String judgment) {
    public CombatNarrationRequest {
        command = Objects.requireNonNull(command, "combat command must not be null");
        if (command.role() == CombatActorRole.PLAYER && (playerInput == null || playerInput.isBlank())) {
            throw new IllegalArgumentException("player input must not be blank");
        }
        playerInput = playerInput == null || playerInput.isBlank() ? null : playerInput.trim();
        if (encounterVersion < 1) throw new IllegalArgumentException("encounter version must be positive");
        judgment = judgment == null ? "" : judgment.trim();
    }

    public static CombatNarrationRequest postResolution(CombatActionCommand command, long encounterVersion,
            Integer diceTotal, String judgment, String playerInput) {
        return new CombatNarrationRequest(command, playerInput, encounterVersion, diceTotal, judgment);
    }

    public boolean hasPlayerInput() {
        return playerInput != null;
    }
}
