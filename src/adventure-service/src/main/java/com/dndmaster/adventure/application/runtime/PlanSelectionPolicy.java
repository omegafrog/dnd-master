package com.dndmaster.adventure.application.runtime;

import java.util.Comparator;
import java.util.List;

/** Safety and continuity are represented by hard admission; scores then remain deterministic. */
public final class PlanSelectionPolicy {
    public PlanSelection select(List<PlanCandidate> candidates, List<PlanSelection.Score> scores) {
        if (candidates == null || candidates.isEmpty()) throw new IllegalArgumentException("at least one valid candidate is required");
        PlanSelection.Score best = scores.stream().filter(score -> candidates.stream().anyMatch(c -> c.candidateId().equals(score.candidateId())))
                .min(Comparator.comparingInt(PlanSelection.Score::agency).reversed()
                        .thenComparing(Comparator.comparingInt(PlanSelection.Score::continuity).reversed())
                        .thenComparing(Comparator.comparingInt(PlanSelection.Score::usefulness).reversed())
                        .thenComparing(Comparator.comparingInt(PlanSelection.Score::interest).reversed())
                        .thenComparingInt(score -> candidates.stream().filter(candidate -> candidate.candidateId().equals(score.candidateId()))
                                .findFirst().orElseThrow().complexity())
                        .thenComparing(PlanSelection.Score::candidateId)).orElseGet(() ->
                        PlanSelection.score(candidates.getFirst().candidateId(), 0, 0, 0, 0, candidates.getFirst().complexity(), List.of("deterministic fallback")));
        PlanCandidate selected = candidates.stream().filter(c -> c.candidateId().equals(best.candidateId())).findFirst().orElse(candidates.getFirst());
        return new PlanSelection(selected, scores);
    }
}
