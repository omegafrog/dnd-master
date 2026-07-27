package com.dndmaster.adventure.domain.adventure;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Immutable draft input consumed when a session starts. */
public record AdventureSessionRuntimeConfiguration(
        ScenarioId scenarioId,
        RuleSetId ruleSetId,
        List<UUID> rulebookIds,
        String engineId,
        List<String> toolIds,
        String initialScene) {
    public AdventureSessionRuntimeConfiguration {
        scenarioId = Objects.requireNonNull(scenarioId, "scenario id must not be null");
        ruleSetId = Objects.requireNonNull(ruleSetId, "rule set id must not be null");
        rulebookIds = List.copyOf(Objects.requireNonNull(rulebookIds, "rulebook ids must not be null"));
        if (rulebookIds.stream().anyMatch(Objects::isNull)) throw new IllegalArgumentException("rulebook ids must not contain null");
        engineId = required(engineId, "engine id");
        toolIds = List.copyOf(Objects.requireNonNull(toolIds, "tool ids must not be null"));
        if (toolIds.stream().anyMatch(tool -> tool == null || tool.isBlank())) throw new IllegalArgumentException("tool ids must not contain blank values");
        initialScene = required(initialScene, "initial scene");
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value.trim();
    }
}
