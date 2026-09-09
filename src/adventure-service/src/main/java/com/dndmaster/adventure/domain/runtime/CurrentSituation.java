package com.dndmaster.adventure.domain.runtime;

import com.dndmaster.adventure.domain.scenario.DetailedStage;
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

    /** Builds the initial runtime context from the prepared first stage instead of its internal situation id. */
    public static CurrentSituation fromOpeningStage(DetailedStage stage, String startingLocation) {
        Objects.requireNonNull(stage, "opening stage must not be null");
        return new CurrentSituation(UUID.randomUUID(), 1, required(startingLocation, "starting location"),
                stage.coreProblem(), stage.threat().core(), stage.funnel().meaning(), null);
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
