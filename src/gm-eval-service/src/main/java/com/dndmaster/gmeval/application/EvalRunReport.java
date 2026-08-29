package com.dndmaster.gmeval.application;

import com.dndmaster.gmeval.domain.*;
import java.time.Instant;
import java.util.*;

public record EvalRunReport(String reportSchemaVersion, EvalRunConfiguration configuration,
                            Instant timestamp, List<CaseReport> cases, Aggregate aggregate) {
    public EvalRunReport {
        reportSchemaVersion = reportSchemaVersion == null ? "1" : reportSchemaVersion;
        timestamp = Objects.requireNonNull(timestamp, "timestamp required");
        cases = List.copyOf(cases == null ? List.of() : cases);
        aggregate = Objects.requireNonNull(aggregate, "aggregate required");
    }
    public record CaseReport(String caseId, String response, EvalResult absolute, PairwiseEvalResult pairwise,
                             String generationMetadata, String generatorFailure) {
        public CaseReport { if (caseId == null || caseId.isBlank()) throw new IllegalArgumentException("caseId required"); }
        public CaseReport(String caseId, String response, EvalResult absolute, PairwiseEvalResult pairwise, String generationMetadata) { this(caseId, response, absolute, pairwise, generationMetadata, null); }
    }
    public record Aggregate(long caseCount, long hardPassCount, long hardFailureCount, long hardUnevaluatedCount,
                            Map<String, HardRate> hardByCategory, Map<String, Double> qualityAverageByCategory,
                            Map<String, Long> pairwiseWinTieLoss) {
        public Aggregate { hardByCategory = Map.copyOf(hardByCategory); qualityAverageByCategory = Map.copyOf(qualityAverageByCategory); pairwiseWinTieLoss = Map.copyOf(pairwiseWinTieLoss); }
    }
    public record HardRate(long pass, long failure, long unevaluated) { public double passRate() { return denominator() == 0 ? 0 : (double) pass / denominator(); } public long denominator() { return pass + failure; } }
}
