package com.dndmaster.adventure.domain.adventure;

import java.util.Optional;

public record AdventureContext(
        String currentScene, String npcState, String pendingAction, String latestJudgment) {
    public AdventureContext {
        currentScene = required(currentScene, "current scene");
        npcState = optional(npcState);
        pendingAction = optional(pendingAction);
        latestJudgment = optional(latestJudgment);
    }

    public Optional<String> npcStateValue() { return Optional.ofNullable(npcState); }
    public Optional<String> pendingActionValue() { return Optional.ofNullable(pendingAction); }
    public Optional<String> latestJudgmentValue() { return Optional.ofNullable(latestJudgment); }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value.trim();
    }

    private static String optional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
