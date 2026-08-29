package com.dndmaster.adventure.application.runtime;

import java.util.List;
import java.util.Objects;
import com.dndmaster.adventure.domain.runtime.narrative.NarrativeContext;
import com.dndmaster.adventure.domain.runtime.narrative.NarrativeState;

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

    /** Builds the gate input from the resolved artifact and actor-scoped runtime projection. */
    public static NarrativeVerificationContext from(ResolvedTurnPlan resolvedPlan, NarrativeState state,
                                                     NarrativeContext actorContext, EvidencePack evidencePack) {
        Objects.requireNonNull(resolvedPlan, "resolved plan must not be null");
        Objects.requireNonNull(state, "narrative state must not be null");
        Objects.requireNonNull(actorContext, "narrative context must not be null");
        Objects.requireNonNull(evidencePack, "evidence pack must not be null");
        List<String> supported = new java.util.ArrayList<>();
        actorContext.worldFacts().forEach(fact -> supported.add(fact.value()));
        evidencePack.all().forEach(evidence -> supported.add(evidence.excerpt()));
        List<String> hidden = state.worldFacts().values().stream()
                .filter(fact -> !actorContext.factsKnownBy().contains(fact.id()))
                .map(com.dndmaster.adventure.domain.runtime.narrative.WorldFact::value).toList();
        String summary = resolvedPlan.plan().scene() + " | " + resolvedPlan.plan().judgment()
                + " | outcomes=" + String.join(",", resolvedPlan.outcomes());
        return new NarrativeVerificationContext(summary, supported, hidden, List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of());
    }

    private static List<String> copy(List<String> values) {
        return List.copyOf(Objects.requireNonNull(values));
    }
}
