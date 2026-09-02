package com.dndmaster.adventure.domain.scenario;

import java.util.List;
import java.util.Objects;

public final class CompilationOutcomePolicy {
    private CompilationOutcomePolicy() {}

    public static CompilationOutcome evaluate(List<CompilationCandidate> candidates) {
        Objects.requireNonNull(candidates, "candidates must not be null");
        boolean requiredIncomplete = candidates.stream().anyMatch(candidate ->
                candidate.required() && candidate.completeness() != CandidateCompleteness.COMPLETE);
        if (requiredIncomplete) return CompilationOutcome.FAILED;
        boolean optionalIncomplete = candidates.stream().anyMatch(candidate ->
                !candidate.required() && candidate.completeness() != CandidateCompleteness.COMPLETE);
        return optionalIncomplete ? CompilationOutcome.COMPLETE_WITH_WARNINGS : CompilationOutcome.COMPLETE;
    }
}
