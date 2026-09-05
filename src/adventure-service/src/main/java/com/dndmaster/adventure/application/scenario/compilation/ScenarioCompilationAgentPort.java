package com.dndmaster.adventure.application.scenario.compilation;

import com.dndmaster.adventure.domain.scenario.ScenarioModel;
import java.util.List;

/** Typed semantic compilation boundary; persistence remains owned by Adventure Runtime. */
@FunctionalInterface
public interface ScenarioCompilationAgentPort {
    ScenarioCompilationAgentResult compile(ScenarioCompilationAgentRequest request);

    record ScenarioCompilationAgentRequest(String operationKey, String storybookContext) {
        public ScenarioCompilationAgentRequest {
            if (operationKey == null || operationKey.isBlank()) throw new IllegalArgumentException("operation key is required");
            if (storybookContext == null || storybookContext.isBlank()) throw new IllegalArgumentException("storybook context is required");
            operationKey = operationKey.trim();
            storybookContext = storybookContext.trim();
        }
    }

    record ScenarioCompilationAgentResult(Status status, ScenarioModel scenarioModel, List<String> diagnostics) {
        public enum Status { READY, BLOCKED }
        public ScenarioCompilationAgentResult {
            status = java.util.Objects.requireNonNull(status, "status must not be null");
            diagnostics = List.copyOf(java.util.Objects.requireNonNull(diagnostics, "diagnostics must not be null"));
            if (status == Status.READY && scenarioModel == null) throw new IllegalArgumentException("ready compilation requires a model");
        }
    }
}
