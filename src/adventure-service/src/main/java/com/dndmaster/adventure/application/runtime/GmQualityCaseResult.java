package com.dndmaster.adventure.application.runtime;

public record GmQualityCaseResult(boolean structuredSuccess, boolean ruleEvidenceCorrect,
                                  boolean planFactConsistent, boolean secretLeak, boolean forbiddenTool,
                                  boolean inventedState, double humanScore) {
    public GmQualityCaseResult {
        if (humanScore < 1.0 || humanScore > 5.0) throw new IllegalArgumentException("human score must be 1..5");
    }
}
