package com.dndmaster.gmeval.domain;

/** Boundary for a structured semantic quality judge. */
@FunctionalInterface
public interface RubricJudgePort {
    RubricJudgeResponse judge(RubricJudgeRequest request);
}
