package com.dndmaster.gmeval.domain;
import java.util.List;
public record PairwiseJudgeRequest(EvalCase evalCase, String responseA, String responseB, List<QualityRubric> rubrics) {
    public PairwiseJudgeRequest { if (evalCase == null || responseA == null || responseA.isBlank() || responseB == null || responseB.isBlank()) throw new IllegalArgumentException("pairwise judge request required"); rubrics = List.copyOf(rubrics == null ? List.of() : rubrics); }
}
