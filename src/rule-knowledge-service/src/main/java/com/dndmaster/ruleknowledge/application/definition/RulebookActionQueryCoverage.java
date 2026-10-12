package com.dndmaster.ruleknowledge.application.definition;

public record RulebookActionQueryCoverage(String queryId, String queryFingerprint, int candidateCount) {
    public RulebookActionQueryCoverage {
        if (queryId == null || queryId.isBlank() || queryFingerprint == null || queryFingerprint.isBlank() || candidateCount < 0)
            throw new IllegalArgumentException("query coverage is invalid");
    }
}
