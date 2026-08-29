package com.dndmaster.gmeval.domain;
import java.util.*;
public record EvalResult(List<HardConstraintResult> hardResults, List<QualityScore> qualityScores, String qualityJudgeFailure) {
 public EvalResult { hardResults=List.copyOf(hardResults); qualityScores=List.copyOf(qualityScores); }
 public EvalResult(List<HardConstraintResult> hardResults, List<QualityScore> qualityScores) { this(hardResults, qualityScores, null); }
 public boolean qualityJudgeFailed() { return qualityJudgeFailure != null && !qualityJudgeFailure.isBlank(); }
}
