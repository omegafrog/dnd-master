package com.dndmaster.gmeval.domain;

import java.util.List;

public record RubricJudgeResponse(List<QualityScore> scores) {
    public RubricJudgeResponse { scores = List.copyOf(scores == null ? List.of() : scores); }
}
