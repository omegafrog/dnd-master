package com.dndmaster.adventure.domain.adventure;

import java.util.List;

public record CombatSkeleton(String objective, String startTrigger, List<CombatParticipant> participants,
        String successOutcome, String failureOutcome, List<SourceFactClaim> rewards) {
    public CombatSkeleton {
        objective = optional(objective);
        startTrigger = optional(startTrigger);
        participants = participants == null ? List.of() : List.copyOf(participants);
        successOutcome = optional(successOutcome);
        failureOutcome = optional(failureOutcome);
        rewards = rewards == null ? List.of() : List.copyOf(rewards);
    }

    public static CombatSkeleton empty() {
        return new CombatSkeleton("", "", List.of(), "", "", List.of());
    }

    public boolean complete() {
        return !objective.isBlank() && !startTrigger.isBlank() && !participants.isEmpty()
                && !successOutcome.isBlank() && !failureOutcome.isBlank();
    }

    private static String optional(String value) {
        return value == null ? "" : value.trim();
    }
}
