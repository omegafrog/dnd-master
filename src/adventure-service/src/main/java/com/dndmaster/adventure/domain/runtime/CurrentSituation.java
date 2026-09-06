package com.dndmaster.adventure.domain.runtime;

import java.util.Objects;
import java.util.UUID;

/** Persisted internal runtime context; it is never a player-facing DTO. */
public record CurrentSituation(UUID situationId, long revision, String location, String problem,
        String threat, String goal, String activeCombatScenarioId) {
    public CurrentSituation {
        Objects.requireNonNull(situationId, "situation id must not be null");
        if (revision < 1) throw new IllegalArgumentException("situation revision must be positive");
        location = required(location, "situation location");
        problem = required(problem, "situation problem");
        threat = required(threat, "situation threat");
        goal = required(goal, "situation goal");
        activeCombatScenarioId = activeCombatScenarioId == null || activeCombatScenarioId.isBlank()
                ? null : activeCombatScenarioId.trim();
    }

    public CurrentSituation(UUID situationId, long revision, String location, String problem,
            String threat, String goal) {
        this(situationId, revision, location, problem, threat, goal, null);
    }

    public CurrentSituation(UUID situationId, long revision, String problem) {
        this(situationId, revision, "unknown", problem, "unknown", problem);
    }

    public static CurrentSituation initial(String startingSituation) {
        return new CurrentSituation(UUID.randomUUID(), 1, "starting area", startingSituation, "unresolved threat", startingSituation, null);
    }

    public CurrentSituation enterCombatScenario(String scenarioId) {
        if (scenarioId == null || scenarioId.isBlank()) throw new IllegalArgumentException("combat scenario id must not be blank");
        return new CurrentSituation(situationId, revision + 1, location, problem, threat, goal, scenarioId);
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value.trim();
    }
}
