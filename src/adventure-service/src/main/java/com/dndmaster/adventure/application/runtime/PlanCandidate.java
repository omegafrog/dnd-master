package com.dndmaster.adventure.application.runtime;

import java.util.Objects;
import java.util.Set;

/** Compact decision candidate. It deliberately contains no prose. */
public record PlanCandidate(String candidateId, TurnPlan plan, String playerIntent, String stateFingerprint,
                            String situationKey, String informationBoundary, Set<String> referencedEntities,
                            boolean preservesAgency, boolean continuitySafe, boolean ruleCompliant, int complexity) {
    public PlanCandidate {
        candidateId = required(candidateId, "candidate id");
        plan = Objects.requireNonNull(plan, "plan must not be null");
        playerIntent = required(playerIntent, "player intent");
        stateFingerprint = required(stateFingerprint, "state fingerprint");
        situationKey = required(situationKey, "situation key");
        informationBoundary = required(informationBoundary, "information boundary");
        referencedEntities = Set.copyOf(Objects.requireNonNull(referencedEntities));
        if (complexity < 1) throw new IllegalArgumentException("complexity must be positive");
    }
    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value.trim();
    }
}
