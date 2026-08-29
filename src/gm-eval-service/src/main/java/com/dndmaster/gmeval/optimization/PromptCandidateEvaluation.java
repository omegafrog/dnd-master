package com.dndmaster.gmeval.optimization;

import java.util.List;
import java.util.Objects;

public record PromptCandidateEvaluation(PromptCandidate candidate, MetricVector metrics,
                                        BaselineDelta baselineDelta, GateDecision gate,
                                        List<String> representativeOutputs) {
    public PromptCandidateEvaluation {
        candidate = Objects.requireNonNull(candidate, "candidate required");
        metrics = Objects.requireNonNull(metrics, "candidate metrics required");
        baselineDelta = Objects.requireNonNull(baselineDelta, "baseline delta required");
        gate = Objects.requireNonNull(gate, "gate decision required");
        representativeOutputs = List.copyOf(representativeOutputs == null ? List.of() : representativeOutputs);
    }

    public static PromptCandidateEvaluation from(PromptCandidate candidate, MetricVector metrics,
                                                   BaselineDelta baselineDelta, List<String> representativeOutputs) {
        List<HardMetric> regressions = java.util.Arrays.stream(HardMetric.values())
                .filter(metric -> baselineDelta.hard(metric) > 0).toList();
        GateDecision decision = regressions.isEmpty() ? GateDecision.pass() : GateDecision.rejected(regressions);
        return new PromptCandidateEvaluation(candidate, metrics, baselineDelta, decision, representativeOutputs);
    }
}
