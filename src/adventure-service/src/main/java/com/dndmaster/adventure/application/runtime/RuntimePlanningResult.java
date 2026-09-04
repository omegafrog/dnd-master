package com.dndmaster.adventure.application.runtime;

import java.util.List;
import java.util.Objects;

/** Planning output plus authoritative outcomes produced while materializing tools. */
public record RuntimePlanningResult(RuntimePlan plan, List<RuntimeCommandOutcome> toolOutcomes,
        RuntimeResolutionProposal resolutionProposal) {
    public RuntimePlanningResult {
        plan = Objects.requireNonNull(plan, "plan must not be null");
        toolOutcomes = List.copyOf(Objects.requireNonNull(toolOutcomes, "tool outcomes must not be null"));
        resolutionProposal = resolutionProposal == null ? RuntimeResolutionProposal.unchanged() : resolutionProposal;
    }

    public RuntimePlanningResult(RuntimePlan plan, List<RuntimeCommandOutcome> toolOutcomes) {
        this(plan, toolOutcomes, RuntimeResolutionProposal.unchanged());
    }
}
