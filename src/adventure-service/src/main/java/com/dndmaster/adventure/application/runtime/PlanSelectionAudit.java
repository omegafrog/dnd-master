package com.dndmaster.adventure.application.runtime;

import java.util.List;
import java.util.Objects;

/** Append-only diagnostic record for one plan-only comparison. */
public record PlanSelectionAudit(int requestedCount, List<String> generatedCandidateIds,
                                 List<CandidateHardFilter.Rejection> rejected,
                                 List<PlanSelection.Score> evaluations, String selectedCandidateId,
                                 String failure) {
    public PlanSelectionAudit {
        if (requestedCount < 1) throw new IllegalArgumentException("requested count must be positive");
        generatedCandidateIds = List.copyOf(Objects.requireNonNull(generatedCandidateIds));
        rejected = List.copyOf(Objects.requireNonNull(rejected));
        evaluations = List.copyOf(Objects.requireNonNull(evaluations));
        selectedCandidateId = selectedCandidateId == null ? "" : selectedCandidateId;
        failure = failure == null ? "" : failure;
    }
}
