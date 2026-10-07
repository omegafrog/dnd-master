package com.dndmaster.adventure.application.combat;

import java.util.Objects;

/** Confirmed player-visible combat material. Adventure Runtime composes the GM input. */
public record CombatNarrationRequest(CombatActionCommand command, String playerInput, long encounterVersion,
                                     Integer diceTotal, String judgment, ConfirmedCombatState combatState,
                                     String actorDisplayName, String targetDisplayName) {
    public CombatNarrationRequest {
        command = Objects.requireNonNull(command, "combat command must not be null");
        if (command.role() == CombatActorRole.PLAYER && (playerInput == null || playerInput.isBlank())) {
            throw new IllegalArgumentException("player input must not be blank");
        }
        playerInput = playerInput == null || playerInput.isBlank() ? null : playerInput.trim();
        if (encounterVersion < 1) throw new IllegalArgumentException("encounter version must be positive");
        combatState = Objects.requireNonNull(combatState, "confirmed combat state must not be null");
        if (combatState.encounterVersion() != encounterVersion) {
            throw new IllegalArgumentException("confirmed combat state version does not match narration request");
        }
        judgment = judgment == null ? "" : judgment.trim();
        actorDisplayName = clean(actorDisplayName);
        targetDisplayName = clean(targetDisplayName);
    }

    public static CombatNarrationRequest postResolution(CombatActionCommand command, long encounterVersion,
            ConfirmedCombatState combatState, Integer diceTotal, String judgment, String playerInput) {
        return postResolution(command, encounterVersion, combatState, diceTotal, judgment, playerInput, null, null);
    }

    public static CombatNarrationRequest postResolution(CombatActionCommand command, long encounterVersion,
            ConfirmedCombatState combatState, Integer diceTotal, String judgment, String playerInput,
            String actorDisplayName, String targetDisplayName) {
        return new CombatNarrationRequest(command, playerInput, encounterVersion, diceTotal, judgment, combatState,
                actorDisplayName, targetDisplayName);
    }

    public boolean hasPlayerInput() {
        return playerInput != null;
    }

    public String confirmedActor() {
        String role = hasPlayerInput() ? "플레이어 전투 참여자"
                : command.role().isAi() ? "AI가 조종하는 전투 참여자" : "플레이어가 아닌 전투 참여자";
        return actorDisplayName == null ? role : role + " " + actorDisplayName;
    }

    private static String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
