package com.dndmaster.adventure.application.runtime;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Immutable, bounded context shared by every compact plan candidate. */
public record PlanningContext(String playerIntent, String stateFingerprint, String situationKey,
                              String informationBoundary, Set<String> supportedEntities,
                              Set<String> revealableFacts, Set<String> forbiddenFacts) {
    public PlanningContext {
        playerIntent = required(playerIntent, "player intent");
        stateFingerprint = required(stateFingerprint, "state fingerprint");
        situationKey = required(situationKey, "situation key");
        informationBoundary = required(informationBoundary, "information boundary");
        supportedEntities = Set.copyOf(Objects.requireNonNull(supportedEntities));
        revealableFacts = Set.copyOf(Objects.requireNonNull(revealableFacts));
        forbiddenFacts = Set.copyOf(Objects.requireNonNull(forbiddenFacts));
    }

    public static int candidateCount(boolean simpleTurn) { return simpleTurn ? 1 : 3; }

    public static int boundedCandidateCount(int requested, boolean simpleTurn) {
        return simpleTurn ? 1 : Math.max(1, requested);
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value.trim();
    }
}
