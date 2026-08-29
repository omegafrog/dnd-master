package com.dndmaster.gmeval.tuning;

import com.dndmaster.gmeval.registry.PromptRole;
import java.util.Objects;

/** Immutable base/tuned evaluation and adoption decision. */
public record TuningEvaluationReport(String evaluationId, String proposalId, PromptRole role,
                                     TrainingArtifact artifact, TuningEvaluationContext context,
                                     TuningMetrics baselineMetrics, TuningMetrics tunedMetrics,
                                     TuningMetrics holdoutBaselineMetrics, TuningMetrics holdoutTunedMetrics,
                                     TuningMetricsDelta evaluationDelta, TuningMetricsDelta holdoutDelta,
                                     TuningGateReport gateReport, TuningLineageDeltaReport lineageDelta) {
    public TuningEvaluationReport {
        evaluationId = required(evaluationId, "evaluation id");
        proposalId = required(proposalId, "proposal id");
        role = Objects.requireNonNull(role, "evaluation role required");
        artifact = Objects.requireNonNull(artifact, "training artifact required");
        context = Objects.requireNonNull(context, "evaluation context required");
        baselineMetrics = Objects.requireNonNull(baselineMetrics, "baseline metrics required");
        tunedMetrics = Objects.requireNonNull(tunedMetrics, "tuned metrics required");
        holdoutBaselineMetrics = Objects.requireNonNull(holdoutBaselineMetrics, "holdout baseline metrics required");
        holdoutTunedMetrics = Objects.requireNonNull(holdoutTunedMetrics, "holdout tuned metrics required");
        evaluationDelta = Objects.requireNonNull(evaluationDelta, "evaluation delta required");
        holdoutDelta = Objects.requireNonNull(holdoutDelta, "holdout delta required");
        gateReport = Objects.requireNonNull(gateReport, "gate report required");
        lineageDelta = Objects.requireNonNull(lineageDelta, "lineage delta required");
        if (artifact.role() != role || !artifact.proposalId().equals(proposalId)
                || !lineageDelta.evaluationId().equals(evaluationId)) throw new IllegalArgumentException("evaluation identity mismatch");
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " required");
        return value.trim();
    }
}
