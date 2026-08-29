package com.dndmaster.gmeval.domain;
import java.util.List;
public record PairwiseJudgeResponse(PairwiseWinner winner, List<PairwiseDimensionPreference> preferences, String reason, String evidence) {
    public PairwiseJudgeResponse { preferences = List.copyOf(preferences == null ? List.of() : preferences); }
}
