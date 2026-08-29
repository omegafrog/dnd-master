package com.dndmaster.gmeval.tuning;

import com.dndmaster.gmeval.registry.DatasetSplit;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/** Executes base and tuned targets with one immutable context, then applies every adoption gate. */
public final class TuningEvaluationService {
    private final TuningGatePolicy policy;
    private final TuningReadinessGate readinessGate = new TuningReadinessGate();

    public TuningEvaluationService(TuningGatePolicy policy) { this.policy = Objects.requireNonNull(policy, "tuning gate policy required"); }

    public TuningEvaluationReport evaluate(TuningProposal proposal, TrainingArtifact artifact,
                                           TuningEvaluationPort evaluator) {
        Objects.requireNonNull(proposal, "tuning proposal required");
        Objects.requireNonNull(artifact, "training artifact required");
        Objects.requireNonNull(evaluator, "tuning evaluator required");
        TuningEligibility eligibility = readinessGate.evaluate(proposal);
        if (!eligibility.eligible()) throw new TuningNotEligibleException(eligibility);
        validateIdentity(proposal, artifact);
        TuningEvaluationContext context = new TuningEvaluationContext(proposal.datasetVersion(), proposal.evalVersion(),
                proposal.holdoutVersion(), artifact.hyperparameters().seed());
        TuningMetrics baseline = Objects.requireNonNull(evaluator.evaluate(proposal.role(), artifact.baseModelVersion(),
                artifact.optimizedPromptVersion(), context, DatasetSplit.DEV), "base evaluation metrics");
        TuningMetrics tuned = Objects.requireNonNull(evaluator.evaluate(proposal.role(), artifact.tunedModelVersion(),
                artifact.optimizedPromptVersion(), context, DatasetSplit.DEV), "tuned evaluation metrics");
        TuningMetrics holdoutBaseline = Objects.requireNonNull(evaluator.evaluate(proposal.role(), artifact.baseModelVersion(),
                artifact.optimizedPromptVersion(), context, DatasetSplit.HOLDOUT), "base holdout metrics");
        TuningMetrics holdoutTuned = Objects.requireNonNull(evaluator.evaluate(proposal.role(), artifact.tunedModelVersion(),
                artifact.optimizedPromptVersion(), context, DatasetSplit.HOLDOUT), "tuned holdout metrics");
        TuningMetricsDelta evaluationDelta = TuningMetricsDelta.between(tuned, baseline);
        TuningMetricsDelta holdoutDelta = TuningMetricsDelta.between(holdoutTuned, holdoutBaseline);
        TuningGateReport gates = TuningGateReport.evaluate(baseline, tuned, holdoutBaseline, holdoutTuned, policy);
        String evaluationId = fingerprint(proposal, artifact, context, baseline, tuned, holdoutBaseline, holdoutTuned);
        TuningLineageDeltaReport lineage = new TuningLineageDeltaReport(evaluationId, proposal.proposalId(), proposal.role(),
                artifact.artifactId(), artifact.baseModelVersion(), artifact.optimizedPromptVersion(), artifact.tunedModelVersion(),
                evaluationDelta, holdoutDelta, null);
        return new TuningEvaluationReport(evaluationId, proposal.proposalId(), proposal.role(), artifact, context,
                baseline, tuned, holdoutBaseline, holdoutTuned, evaluationDelta, holdoutDelta, gates, lineage);
    }

    private static void validateIdentity(TuningProposal proposal, TrainingArtifact artifact) {
        if (artifact.role() != proposal.role() || !artifact.proposalId().equals(proposal.proposalId())
                || !artifact.datasetVersion().equals(proposal.datasetVersion())
                || !artifact.holdoutVersion().equals(proposal.holdoutVersion())
                || !artifact.baseModelVersion().equals(proposal.baseModelVersion())
                || !artifact.optimizedPromptVersion().equals(proposal.optimizedPromptVersion())
                || !artifact.tunedModelVersion().equals(proposal.comparison().tunedModelVersion())) {
            throw new IllegalArgumentException("training artifact and proposal identity mismatch");
        }
    }

    private static String fingerprint(Object... values) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(java.util.Arrays.deepToString(values).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }
}
