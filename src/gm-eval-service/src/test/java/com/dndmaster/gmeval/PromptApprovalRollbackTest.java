package com.dndmaster.gmeval;

import static org.junit.jupiter.api.Assertions.*;

import com.dndmaster.gmeval.optimization.*;
import com.dndmaster.gmeval.registry.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PromptApprovalRollbackTest {
    @Test
    void candidateNeedsHoldoutAndRepresentativeReviewBeforeActivation() {
        PromptRegistry registry = registryWithBaselines();
        PromptOptimizationRun run = run("writer-run", PromptRole.WRITER, "writer-candidate", "1.1.0");
        PromptVersion candidate = run.selected().candidate().promptArtifact().promptVersion();

        assertThrows(IllegalArgumentException.class, () -> registry.submitForReview(run, "writer-candidate",
                holdout("other-dataset", true)));
        assertThrows(IllegalStateException.class, () -> registry.activate(candidate, version(PromptRole.WRITER, "1.0.0"), "operator"));

        registry.submitForReview(run, "writer-candidate", holdout("dataset-v1", true));
        assertEquals(PromptArtifactStatus.PENDING_REVIEW, registry.artifact(candidate).orElseThrow().status());
        assertThrows(IllegalArgumentException.class, () -> registry.review(candidate,
                new ReviewerDecision("reviewer-1", true, "looks good", List.of(), null)));

        registry.review(candidate, new ReviewerDecision("reviewer-1", true, "representative samples pass",
                List.of("case-1", "case-2"), null));
        assertEquals(PromptArtifactStatus.APPROVED, registry.artifact(candidate).orElseThrow().status());
    }

    @Test
    void activationUsesRoleScopedCompareAndSetAndKeepsPreviousApprovedVersion() {
        PromptRegistry registry = registryWithBaselines();
        PromptVersion baselineWriter = version(PromptRole.WRITER, "1.0.0");
        PromptVersion candidate = prepareApproved(registry, run("writer-run", PromptRole.WRITER, "writer-candidate", "1.1.0"));
        PromptVersion otherRole = version(PromptRole.PLANNER, "1.0.0");

        PromptRuntimeConfiguration active = registry.activate(candidate, baselineWriter, "operator");
        assertEquals(candidate, active.promptVersion());
        assertEquals(otherRole, registry.active(PromptRole.PLANNER).promptVersion());
        assertEquals(PromptArtifactStatus.APPROVED, registry.artifact(baselineWriter).orElseThrow().status());

        assertThrows(StalePromptActivationException.class, () -> registry.activate(candidate, baselineWriter, "stale-operator"));
    }

    @Test
    void rollbackOnlyTargetsApprovedPreviousVersionAndLeavesLineageAndAudit() {
        PromptRegistry registry = registryWithBaselines();
        PromptOptimizationRun run = run("writer-run", PromptRole.WRITER, "writer-candidate", "1.1.0");
        PromptVersion candidate = prepareApproved(registry, run);
        registry.activate(candidate, version(PromptRole.WRITER, "1.0.0"), "reviewer-1");
        assertEquals("writer-run", registry.active(PromptRole.WRITER).optimizationRunId());

        PromptRuntimeConfiguration restored = registry.rollback(PromptRole.WRITER, version(PromptRole.WRITER, "1.0.0"),
                candidate, "operator");

        assertNull(restored.optimizationRunId());
        assertEquals(version(PromptRole.WRITER, "1.0.0"), restored.promptVersion());
        assertEquals(candidate, registry.artifact(candidate).orElseThrow().promptVersion());
        assertEquals(PromptArtifactStatus.ROLLED_BACK, registry.artifact(candidate).orElseThrow().status());
        assertTrue(registry.audit().stream().anyMatch(a -> a.action() == PromptAuditAction.ROLLBACK
                && a.promptVersion().equals(version(PromptRole.WRITER, "1.0.0"))));
    }

    @Test
    void approvalAndAuditSurviveJsonPersistence() throws Exception {
        Path registryFile = Files.createTempFile("prompt-registry", ".json");
        Path approvalFile = Files.createTempFile("prompt-approvals", ".json");
        PromptRegistry first = new PromptRegistry(new JsonPromptRegistryStore(registryFile),
                new JsonPromptApprovalStore(approvalFile));
        first.registerBaseline(artifact(PromptRole.WRITER, "1.0.0", null, true));
        PromptOptimizationRun run = run("writer-run", PromptRole.WRITER, "writer-candidate", "1.1.0");
        PromptVersion candidate = prepareApproved(first, run);
        first.activate(candidate, version(PromptRole.WRITER, "1.0.0"), "operator");

        PromptRegistry restored = new PromptRegistry(new JsonPromptRegistryStore(registryFile),
                new JsonPromptApprovalStore(approvalFile));
        assertEquals(candidate, restored.active(PromptRole.WRITER).promptVersion());
        assertTrue(restored.audit().stream().anyMatch(a -> a.action() == PromptAuditAction.ACTIVATE));
        assertEquals("writer-run", restored.active(PromptRole.WRITER).optimizationRunId());
    }

    private static PromptVersion prepareApproved(PromptRegistry registry, PromptOptimizationRun run) {
        PromptVersion candidate = run.selected().candidate().promptArtifact().promptVersion();
        registry.submitForReview(run, run.selected().candidate().candidateId(), holdout("dataset-v1", true));
        registry.review(candidate, new ReviewerDecision("reviewer-1", true, "approved after samples",
                List.of("case-1"), null));
        return candidate;
    }

    private static PromptRegistry registryWithBaselines() {
        PromptRegistry registry = new PromptRegistry();
        registry.registerBaseline(artifact(PromptRole.WRITER, "1.0.0", null, true));
        registry.registerBaseline(artifact(PromptRole.PLANNER, "1.0.0", null, true));
        return registry;
    }

    private static PromptOptimizationRun run(String runId, PromptRole role, String candidateId, String candidateVersion) {
        PromptArtifact baseline = artifact(role, "1.0.0", null, true);
        PromptCandidate candidate = new PromptCandidate(candidateId,
                artifact(role, candidateVersion, baseline.promptVersion(), false), 42);
        MetricVector metrics = new MetricVector(Map.of(), Map.of("quality", 5.0));
        PromptCandidateEvaluation evaluation = PromptCandidateEvaluation.from(candidate, metrics,
                BaselineDelta.between(metrics, new MetricVector(Map.of(), Map.of("quality", 3.0))), List.of("sample"));
        return new PromptOptimizationRun(runId, role, "dataset-v1", "eval-v1", 42, baseline,
                List.of(evaluation), evaluation);
    }

    private static HoldoutEvaluation holdout(String dataset, boolean pass) {
        MetricVector candidate = new MetricVector(pass ? Map.of() : Map.of(HardMetric.RULE_VIOLATION, 1), Map.of("quality", 4.0));
        MetricVector baseline = new MetricVector(Map.of(), Map.of("quality", 3.0));
        return HoldoutEvaluation.of(dataset, "eval-v1", candidate, baseline);
    }

    private static PromptVersion version(PromptRole role, String value) { return new PromptVersion(role, value); }

    private static PromptArtifact artifact(PromptRole role, String version, PromptVersion parent, boolean baseline) {
        return new PromptArtifact(new PromptVersion(role, version), parent, "prompt-" + version,
                "schema-1", List.of("context"), "after-context", "model-" + role.name().toLowerCase(), "config-1",
                "dataset-v1", "eval-v1", baseline, PromptArtifactStatus.DRAFT);
    }
}
