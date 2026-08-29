package com.dndmaster.gmeval.application;

import java.util.Objects;

/** All identifiers that make a run reproducible and comparable. */
public record EvalRunConfiguration(String runId, String datasetVersion, String model,
                                   String promptVersion, String configurationVersion,
                                   String turnPlanSchemaVersion, String generatorVersion) {
    public EvalRunConfiguration {
        require(runId, "runId"); require(datasetVersion, "datasetVersion"); require(model, "model");
        require(promptVersion, "promptVersion"); require(configurationVersion, "configurationVersion");
        turnPlanSchemaVersion = Objects.requireNonNullElse(turnPlanSchemaVersion, "not-applicable");
        generatorVersion = Objects.requireNonNullElse(generatorVersion, "supplied-response");
    }
    private static void require(String value, String name) { if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " required"); }
}
