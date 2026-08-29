package com.dndmaster.gmeval.tuning;

/** Immutable conditions shared by base and tuned evaluations. */
public record TuningEvaluationContext(String datasetVersion, String evalVersion,
                                      String holdoutVersion, long seed) {
    public TuningEvaluationContext {
        datasetVersion = required(datasetVersion, "evaluation dataset version");
        evalVersion = required(evalVersion, "evaluation version");
        holdoutVersion = required(holdoutVersion, "holdout version");
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " required");
        return value.trim();
    }
}
