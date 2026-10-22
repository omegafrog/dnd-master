package com.dndmaster.adventure.domain.runtime;

import com.dndmaster.adventure.domain.scenario.DetailedStage;
import java.util.Objects;
import java.util.UUID;

/** Persisted internal runtime context; it is never a player-facing DTO. */
public record CurrentSituation(UUID situationId, long revision, String location, String problem,
        String threat, String goal, String activeCombatScenarioId, String firstNarration,
        String title, String description, UUID mapDefinitionId) {
    public CurrentSituation {
        Objects.requireNonNull(situationId, "situation id must not be null");
        if (revision < 1) throw new IllegalArgumentException("situation revision must be positive");
        location = required(location, "situation location");
        problem = required(problem, "situation problem");
        threat = required(threat, "situation threat");
        goal = required(goal, "situation goal");
        activeCombatScenarioId = activeCombatScenarioId == null || activeCombatScenarioId.isBlank()
                ? null : activeCombatScenarioId.trim();
        firstNarration = optional(firstNarration);
        title = title == null || title.isBlank() ? location : title.trim();
        description = description == null || description.isBlank() ? problem : description.trim();
    }

    public CurrentSituation(UUID situationId, long revision, String location, String problem,
            String threat, String goal) {
        this(situationId, revision, location, problem, threat, goal, null, null, null, null, null);
    }

    public CurrentSituation(UUID situationId, long revision, String location, String problem,
            String threat, String goal, String activeCombatScenarioId) {
        this(situationId, revision, location, problem, threat, goal, activeCombatScenarioId, null, null, null, null);
    }

    public CurrentSituation(UUID situationId, long revision, String location, String problem,
            String threat, String goal, String activeCombatScenarioId, String firstNarration) {
        this(situationId, revision, location, problem, threat, goal, activeCombatScenarioId, firstNarration,
                null, null, null);
    }

    public CurrentSituation(UUID situationId, long revision, String problem) {
        this(situationId, revision, "unknown", problem, "unknown", problem);
    }

    public String promptContext() {
        return "CurrentSituation[currentStateRecordId=" + situationId + ", revision=" + revision
                + ", location=" + location + ", problem=" + problem + ", threat=" + threat + ", goal=" + goal
                + ", title=" + title + ", description=" + description + ", mapDefinitionId=" + mapDefinitionId
                + ", activeCombatScenarioId=" + activeCombatScenarioId + ", firstNarration=" + firstNarration + "]";
    }

    public static CurrentSituation initial(String startingSituation) {
        return new CurrentSituation(UUID.randomUUID(), 1, "starting area", startingSituation, "unresolved threat", startingSituation, null, null);
    }

    /** Builds the initial runtime context from the prepared first stage instead of its internal situation id. */
    public static CurrentSituation fromOpeningStage(DetailedStage stage, String startingLocation) {
        Objects.requireNonNull(stage, "opening stage must not be null");
        return new CurrentSituation(UUID.randomUUID(), 1, required(startingLocation, "starting location"),
                stage.coreProblem(), stage.threat().core(), stage.funnel().meaning(), null, null);
    }

    public CurrentSituation enterCombatScenario(String scenarioId) {
        if (scenarioId == null || scenarioId.isBlank()) throw new IllegalArgumentException("combat scenario id must not be blank");
        return new CurrentSituation(situationId, revision + 1, location, problem, threat, goal, scenarioId, firstNarration,
                title, description, mapDefinitionId);
    }

    public CurrentSituation afterCombat(String summary, boolean enemiesDefeated) {
        if (summary == null || summary.isBlank()) throw new IllegalArgumentException("combat end summary must not be blank");
        if (enemiesDefeated) {
            return new CurrentSituation(situationId, revision + 1, location, summary, summary,
                    "전투가 끝난 뒤 다음 행동을 결정한다", null, null, null, null, null);
        }
        return new CurrentSituation(situationId, revision + 1, location, problem, threat, goal, null, firstNarration,
                title, description, mapDefinitionId);
    }

    public CurrentSituation withFirstNarration(String narration) {
        if (firstNarration != null || narration == null || narration.isBlank()) return this;
        return new CurrentSituation(situationId, revision, location, problem, threat, goal, activeCombatScenarioId, narration,
                title, description, mapDefinitionId);
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value.trim();
    }

    private static String optional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
