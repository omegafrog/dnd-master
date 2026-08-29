package com.dndmaster.adventure.application.runtime;

import java.util.List;
import java.util.Objects;

public record PlanSelection(PlanCandidate selected, List<Score> evaluations) {
    public PlanSelection {
        selected = Objects.requireNonNull(selected, "selected candidate must not be null");
        evaluations = List.copyOf(Objects.requireNonNull(evaluations));
    }
    public record Score(String candidateId, int agency, int continuity, int usefulness, int interest,
                        int simplicity, List<String> evidence) {
        public Score { evidence = List.copyOf(Objects.requireNonNull(evidence)); }
    }
    public static Score score(String id, int agency, int continuity, int usefulness, int interest, int simplicity, List<String> evidence) {
        return new Score(id, agency, continuity, usefulness, interest, simplicity, evidence);
    }
}
