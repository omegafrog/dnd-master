package com.dndmaster.adventure.application.runtime;

/** Immutable deployment gate result for provider conformance evaluation. */
public record GmQualityGateReport(int totalCases, int structuredSuccesses, int ruleEvidencePasses,
                                  int planFactPasses, int secretViolations, int forbiddenToolViolations,
                                  int inventedStateViolations, double humanScore) {
    public GmQualityGateReport {
        if (totalCases < 1 || structuredSuccesses < 0 || ruleEvidencePasses < 0 || planFactPasses < 0
                || structuredSuccesses > totalCases || ruleEvidencePasses > totalCases || planFactPasses > totalCases
                || secretViolations < 0 || forbiddenToolViolations < 0 || inventedStateViolations < 0
                || secretViolations > totalCases || forbiddenToolViolations > totalCases
                || inventedStateViolations > totalCases || !Double.isFinite(humanScore)
                || humanScore < 1.0 || humanScore > 5.0) {
            throw new IllegalArgumentException("invalid GM quality gate report");
        }
    }

    public boolean passed() {
        return secretViolations == 0 && forbiddenToolViolations == 0 && inventedStateViolations == 0
                && rate(structuredSuccesses) >= 0.99
                && rate(ruleEvidencePasses) >= 0.95
                && rate(planFactPasses) >= 0.95
                && humanScore >= 4.0;
    }

    private double rate(int successes) { return successes / (double) totalCases; }
}
