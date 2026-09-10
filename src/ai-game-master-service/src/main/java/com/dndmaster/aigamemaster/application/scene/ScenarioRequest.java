package com.dndmaster.aigamemaster.application.scene;

import com.dndmaster.aigamemaster.application.rule.SourceEvidence;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Inputs for one grounded scene, including the action that has already happened. */
public record ScenarioRequest(
        UUID scenarioId,
        String selectedScenario,
        String currentContext,
        UUID ruleSetId,
        List<SourceEvidence> evidence,
        String playerAction,
        List<String> recentActions,
        List<String> runtimeFacts) {
    public ScenarioRequest {
        scenarioId = Objects.requireNonNull(scenarioId, "scenario id must not be null");
        selectedScenario = required(selectedScenario, "scenario");
        currentContext = required(currentContext, "context");
        ruleSetId = Objects.requireNonNull(ruleSetId, "rule set id must not be null");
        evidence = List.copyOf(Objects.requireNonNull(evidence, "evidence must not be null"));
        playerAction = optional(playerAction);
        recentActions = List.copyOf(recentActions == null ? List.of() : recentActions)
                .stream().map(ScenarioRequest::optional).filter(value -> !value.isBlank()).toList();
        runtimeFacts = List.copyOf(runtimeFacts == null ? List.of() : runtimeFacts)
                .stream().map(ScenarioRequest::optional).filter(value -> !value.isBlank()).toList();
    }

    public ScenarioRequest(UUID scenarioId, String selectedScenario, String currentContext,
            UUID ruleSetId, List<SourceEvidence> evidence, String playerAction,
            List<String> recentActions) {
        this(scenarioId, selectedScenario, currentContext, ruleSetId, evidence, playerAction, recentActions, List.of());
    }

    public ScenarioRequest(UUID scenarioId, String selectedScenario, String currentContext,
            UUID ruleSetId, List<SourceEvidence> evidence) {
        this(scenarioId, selectedScenario, currentContext, ruleSetId, evidence, "", List.of(), List.of());
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " required");
        return value.trim();
    }

    private static String optional(String value) {
        return value == null ? "" : value.trim();
    }
}
