package com.dndmaster.gmeval.domain;

import java.util.List;

public record RubricJudgeRequest(EvalCase evalCase, String response, List<QualityRubric> rubrics) {
    public RubricJudgeRequest {
        if (evalCase == null || response == null || response.isBlank()) throw new IllegalArgumentException("judge request required");
        rubrics = List.copyOf(rubrics == null ? List.of() : rubrics);
    }
}
