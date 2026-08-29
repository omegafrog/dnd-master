package com.dndmaster.gmeval.domain;
import java.util.*;
public record EvalResult(List<HardConstraintResult> hardResults, List<QualityScore> qualityScores) {
 public EvalResult { hardResults=List.copyOf(hardResults); qualityScores=List.copyOf(qualityScores); }
}
