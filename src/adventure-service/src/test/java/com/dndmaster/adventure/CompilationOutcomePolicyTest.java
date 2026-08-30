package com.dndmaster.adventure;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.dndmaster.adventure.domain.scenario.CandidateCompleteness;
import com.dndmaster.adventure.domain.scenario.CandidateRecoverability;
import com.dndmaster.adventure.domain.scenario.CompilationCandidate;
import com.dndmaster.adventure.domain.scenario.CompilationOutcome;
import com.dndmaster.adventure.domain.scenario.CompilationOutcomePolicy;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CompilationOutcomePolicyTest {
    @Test
    void requiredIncompleteFails() {
        assertEquals(CompilationOutcome.FAILED, CompilationOutcomePolicy.evaluate(List.of(candidate(true, CandidateCompleteness.PARTIAL))));
        assertEquals(CompilationOutcome.FAILED, CompilationOutcomePolicy.evaluate(List.of(candidate(true, CandidateCompleteness.INVALID))));
    }

    @Test
    void optionalIncompleteWarnsAndAllCompleteSucceeds() {
        assertEquals(CompilationOutcome.COMPLETE_WITH_WARNINGS,
                CompilationOutcomePolicy.evaluate(List.of(candidate(false, CandidateCompleteness.INVALID))));
        assertEquals(CompilationOutcome.COMPLETE,
                CompilationOutcomePolicy.evaluate(List.of(candidate(true, CandidateCompleteness.COMPLETE))));
    }

    private static CompilationCandidate candidate(boolean required, CandidateCompleteness completeness) {
        return CompilationCandidate.of(UUID.randomUUID(), "test", "DICE_ROLL", required, completeness, List.of(),
                CandidateRecoverability.NON_REPAIRABLE, null, null);
    }
}
