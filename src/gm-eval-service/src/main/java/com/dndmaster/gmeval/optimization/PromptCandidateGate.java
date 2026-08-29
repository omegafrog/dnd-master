package com.dndmaster.gmeval.optimization;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Hard-first candidate policy. Soft scores never compensate for a hard regression. */
public final class PromptCandidateGate {
    public GateDecision evaluate(MetricVector candidate, MetricVector baseline) {
        Objects.requireNonNull(candidate, "candidate metrics required");
        Objects.requireNonNull(baseline, "baseline metrics required");
        List<HardMetric> regressions = java.util.Arrays.stream(HardMetric.values())
                .filter(metric -> candidate.hard(metric) > baseline.hard(metric)).toList();
        return regressions.isEmpty() ? GateDecision.pass() : GateDecision.rejected(regressions);
    }

    public PromptCandidateEvaluation evaluate(PromptCandidate candidate, MetricVector metrics,
                                              MetricVector baseline, List<String> representativeOutputs) {
        BaselineDelta delta = BaselineDelta.between(metrics, baseline);
        GateDecision decision = evaluate(metrics, baseline);
        return new PromptCandidateEvaluation(candidate, metrics, delta, decision, representativeOutputs);
    }

    public PromptCandidateEvaluation selectBest(List<PromptCandidateEvaluation> evaluations) {
        return selectBestOrEmpty(evaluations).orElseThrow(() -> new IllegalStateException("all prompt candidates failed hard gates"));
    }

    public Optional<PromptCandidateEvaluation> selectBestOrEmpty(List<PromptCandidateEvaluation> evaluations) {
        if (evaluations == null || evaluations.isEmpty()) throw new IllegalArgumentException("candidate evaluations required");
        return evaluations.stream().filter(value -> value.gate().accepted())
                .sorted(Comparator.comparingDouble((PromptCandidateEvaluation value) -> value.metrics().softScore()).reversed()
                        .thenComparingInt(value -> value.metrics().totalHardViolations())
                        .thenComparing(value -> value.candidate().candidateId())
                        .thenComparing(value -> value.candidate().promptArtifact().promptVersion().value()))
                .findFirst();
    }
}
