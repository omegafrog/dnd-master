package com.dndmaster.gmeval;

import static org.junit.jupiter.api.Assertions.*;

import com.dndmaster.gmeval.registry.DatasetSplit;
import com.dndmaster.gmeval.registry.PromptRole;
import com.dndmaster.gmeval.tuning.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TuningReadinessGateTest {
    @Test
    void missingPrerequisitesAndRepeatedFailureEvidenceRejectProposal() {
        TuningProposal proposal = proposal(PromptRole.WRITER, List.of(), List.of());

        TuningEligibility result = new TuningReadinessGate().evaluate(proposal);

        assertFalse(result.eligible());
        assertTrue(result.rejectionReasons().contains(TuningRejectionReason.MISSING_PREREQUISITE));
        assertTrue(result.rejectionReasons().contains(TuningRejectionReason.INSUFFICIENT_FAILURE_EVIDENCE));
        assertEquals(TuningProposalStatus.NOT_JUSTIFIED, result.status());
    }

    @Test
    void unsafeAndLeakingSamplesAreExcludedFromTrainingProjection() {
        TuningSample safe = sample("safe", PromptRole.WRITER, DatasetSplit.TRAIN, "adv-safe", "session-safe",
                List.of(FailureEvidence.resolved("e-safe", TuningFailureCategory.UNRESOLVED_HALLUCINATION)));
        TuningSample secret = sample("secret", PromptRole.WRITER, DatasetSplit.TRAIN, "adv-b", "session-b",
                List.of(FailureEvidence.unresolved("e-secret", TuningFailureCategory.SECRET_LEAK)));
        TuningSample leakyTrain = sample("leaky-train", PromptRole.WRITER, DatasetSplit.TRAIN, "adv-a", "session-a",
                List.of(FailureEvidence.resolved("e-leaky-train", TuningFailureCategory.UNRESOLVED_HALLUCINATION)));
        TuningSample leaking = sample("leaking", PromptRole.WRITER, DatasetSplit.HOLDOUT, "adv-a", "session-a",
                List.of(FailureEvidence.resolved("e-leak", TuningFailureCategory.UNRESOLVED_HALLUCINATION)));
        TuningProposal proposal = proposal(PromptRole.WRITER, List.of(safe, secret, leakyTrain, leaking),
                List.of(FailureEvidence.resolved("repeat-1", TuningFailureCategory.UNRESOLVED_HALLUCINATION),
                        FailureEvidence.resolved("repeat-2", TuningFailureCategory.UNRESOLVED_HALLUCINATION)));

        TuningEligibility result = new TuningReadinessGate().evaluate(proposal);

        assertTrue(result.eligible());
        assertEquals(List.of("safe"), result.eligibleSamples().stream().map(TuningSample::sampleId).toList());
        assertEquals(TuningProposalStatus.ELIGIBLE, result.status());
        assertTrue(result.exclusions().stream().anyMatch(value -> value.sampleId().equals("secret")
                && value.reason() == TuningRejectionReason.UNSAFE_SAMPLE));
        assertTrue(result.exclusions().stream().anyMatch(value -> value.sampleId().equals("leaking")
                && value.reason() == TuningRejectionReason.SPLIT_LEAKAGE));
    }

    @Test
    void roleAndComparisonAreScopedToOneRole() {
        TuningSample planner = sample("planner", PromptRole.PLANNER, DatasetSplit.TRAIN, "adv-p", "session-p",
                List.of(FailureEvidence.resolved("e-p", TuningFailureCategory.RULE_CONTRADICTION)));
        TuningProposal proposal = proposal(PromptRole.WRITER, List.of(planner),
                List.of(FailureEvidence.resolved("repeat-1", TuningFailureCategory.RULE_CONTRADICTION),
                        FailureEvidence.resolved("repeat-2", TuningFailureCategory.RULE_CONTRADICTION)));

        TuningEligibility result = new TuningReadinessGate().evaluate(proposal);

        assertFalse(result.eligible());
        assertTrue(result.rejectionReasons().contains(TuningRejectionReason.ROLE_MISMATCH));
        assertEquals(PromptRole.WRITER, proposal.comparison().role());
    }

    @Test
    void persistsRejectedAuditAndOperatorProjection() throws Exception {
        TuningProposal proposal = proposal(PromptRole.WRITER, List.of(),
                List.of(FailureEvidence.resolved("taxonomy", TuningFailureCategory.UNRESOLVED_HALLUCINATION)));
        TuningProposalRepository repository = new TuningProposalRepository(
                new JsonTuningProposalStore(Files.createTempFile("tuning-proposals", ".json")));

        TuningProposalApplicationService service = new TuningProposalApplicationService(repository);
        TuningProposalView view = service.evaluate(proposal);
        assertEquals(TuningProposalStatus.NOT_JUSTIFIED, view.status());
        assertTrue(view.rejectionReasons().contains(TuningRejectionReason.MISSING_PREREQUISITE));
        assertEquals(proposal.proposalId(), view.proposalId());
        assertTrue(view.failureTaxonomy().contains(TuningFailureCategory.UNRESOLVED_HALLUCINATION));
        assertEquals(1, service.audit(proposal.proposalId()).size());
        assertEquals(view.failureTaxonomy(), service.failureTaxonomy(proposal.proposalId()));
    }

    private static TuningProposal proposal(PromptRole role, List<TuningSample> samples,
                                           List<FailureEvidence> repeatedFailures) {
        return new TuningProposal("proposal-1", role, TuningMethod.SFT, "contract-v1", "eval-v1",
                "dataset-v1", "holdout-v1", "base-model", "optimized-prompt-v1", true, true,
                true, true, samples, repeatedFailures, new TuningComparison(role, "base-model",
                "optimized-prompt-v1", "base-model", "eval-v1", "holdout-v1", metric(0, 0), metric(0, 0)));
    }

    private static TuningSample sample(String id, PromptRole role, DatasetSplit split, String adventure,
                                       String session, List<FailureEvidence> evidence) {
        return new TuningSample(id, role, "dataset-v1", split, adventure, session, "scene-" + id,
                "source/" + id, true, true, evidence);
    }

    private static TuningMetrics metric(int hard, int soft) {
        return new TuningMetrics(Map.of("hard", hard), Map.of("quality", (double) soft));
    }
}
