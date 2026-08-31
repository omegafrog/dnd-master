package com.dndmaster.aigamemaster.infrastructure.ai;

import java.util.List;

public record GmRepairContext(String rawResponse, List<GmCandidateViolation> violations) {
    public GmRepairContext {
        rawResponse = rawResponse == null ? "" : rawResponse;
        violations = violations == null ? List.of() : List.copyOf(violations);
    }
}
