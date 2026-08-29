package com.dndmaster.adventure.application.runtime;

import java.util.List;
import java.util.Objects;

/** Minimum, actor-scoped facts and forbidden claims supplied to the final gate. */
public record NarrativeVerificationContext(String turnPlanSummary, List<String> supportedFacts,
        List<String> hiddenFacts, List<String> ruleMismatches, List<String> agencyViolations,
        List<String> npcKnowledgeViolations, List<String> turnPlanDeviations,
        List<String> stateContradictions, List<String> unsupportedFacts) {
    public NarrativeVerificationContext {
        turnPlanSummary = turnPlanSummary == null ? "" : turnPlanSummary.trim();
        supportedFacts = copy(supportedFacts); hiddenFacts = copy(hiddenFacts);
        ruleMismatches = copy(ruleMismatches); agencyViolations = copy(agencyViolations);
        npcKnowledgeViolations = copy(npcKnowledgeViolations); turnPlanDeviations = copy(turnPlanDeviations);
        stateContradictions = copy(stateContradictions); unsupportedFacts = copy(unsupportedFacts);
    }

    public static NarrativeVerificationContext of(String scene, List<String> supportedFacts) {
        return new NarrativeVerificationContext(scene, supportedFacts, List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of());
    }

    private static List<String> copy(List<String> values) {
        return List.copyOf(Objects.requireNonNull(values));
    }
}
