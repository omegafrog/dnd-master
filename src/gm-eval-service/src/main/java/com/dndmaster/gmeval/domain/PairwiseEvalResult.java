package com.dndmaster.gmeval.domain;
import java.util.List;
public record PairwiseEvalResult(PairwiseWinner winner, List<PairwiseDimensionPreference> preferences,
                                 String reason, String evidence, EvalResult responseA, EvalResult responseB,
                                 String judgeFailure) {
    public PairwiseEvalResult { preferences = List.copyOf(preferences == null ? List.of() : preferences); }
    public boolean judgeFailed() { return judgeFailure != null && !judgeFailure.isBlank(); }
}
