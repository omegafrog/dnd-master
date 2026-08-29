package com.dndmaster.gmeval;

import static org.junit.jupiter.api.Assertions.*;

import com.dndmaster.gmeval.registry.DatasetSplit;
import com.dndmaster.gmeval.registry.PromptArtifact;
import com.dndmaster.gmeval.registry.PromptArtifactStatus;
import com.dndmaster.gmeval.registry.PromptRegistry;
import com.dndmaster.gmeval.registry.PromptRole;
import com.dndmaster.gmeval.registry.PromptVersion;
import com.dndmaster.gmeval.tuning.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class RoleScopedTuningEvaluationTest {
    @Test
    void onlyEligibleRoleTrainerRunsAndArtifactIsPersisted() {
        CountingTrainer writer = new CountingTrainer(PromptRole.WRITER);
        TrainingArtifactRepository repository = new TrainingArtifactRepository(new InMemoryTrainingArtifactStore());
        RoleTuningApplicationService service = new RoleTuningApplicationService(new TuningReadinessGate(),
                Map.of(PromptRole.WRITER, writer), repository);

        TrainingArtifact artifact = service.train(eligibleProposal(PromptRole.WRITER), hyperparameters());

        assertEquals(1, writer.calls.get());
        assertEquals("proposal-1", artifact.proposalId());
        assertEquals("tuned-writer-v1", repository.find(artifact.artifactId()).orElseThrow().tunedModelVersion());
        assertThrows(TuningNotEligibleException.class,
                () -> service.train(ineligibleProposal(PromptRole.WRITER), hyperparameters()));
        assertEquals(1, writer.calls.get(), "ineligible proposals must not invoke trainer");
    }

    @Test
    void failedProviderDoesNotPersistPartialTrainingArtifact() {
        TrainingArtifactRepository repository = new TrainingArtifactRepository(new InMemoryTrainingArtifactStore());
        RoleTuningPort failing = new RoleTuningPort() {
            @Override public PromptRole role() { return PromptRole.WRITER; }
            @Override public TrainingArtifact train(TuningTrainingRequest request) { throw new IllegalStateException("provider unavailable"); }
        };
        RoleTuningApplicationService service = new RoleTuningApplicationService(new TuningReadinessGate(),
                Map.of(PromptRole.WRITER, failing), repository);

        assertThrows(IllegalStateException.class,
                () -> service.train(eligibleProposal(PromptRole.WRITER), hyperparameters()));
        assertTrue(repository.list().isEmpty());
    }

    @Test
    void baseAndTunedUseIdenticalEvaluationConditionsAndAllGatesPass() {
        TrainingArtifact artifact = artifact();
        List<EvaluationCall> calls = new ArrayList<>();
        TuningEvaluationPort evaluator = (role, model, prompt, context, split) -> {
            calls.add(new EvaluationCall(role, model, prompt, context, split));
            boolean tuned = model.equals("tuned-writer-v1");
            return metrics(tuned ? 4.0 : 3.0, 0, tuned ? 11 : 10, tuned ? 110 : 100,
                    tuned ? Set.of() : Set.of(TuningFailureCategory.RULE_CONTRADICTION));
        };

        TuningEvaluationReport report = new TuningEvaluationService(new TuningGatePolicy(.5, 0.0, 20, 200))
                .evaluate(eligibleProposal(PromptRole.WRITER), artifact, evaluator);

        assertTrue(report.gateReport().passed());
        assertEquals(4, calls.size());
        assertEquals(calls.get(0).context(), calls.get(1).context());
        assertEquals(calls.get(2).context(), calls.get(3).context());
        assertEquals(calls.get(0).context(), calls.get(2).context());
        assertEquals("optimized-prompt-v1", calls.get(1).prompt());
        assertEquals("optimized-prompt-v1", calls.get(3).prompt());
        assertTrue(report.evaluationDelta().softScoreDelta().get("quality") > 0);
        assertTrue(report.holdoutDelta().softScoreDelta().get("quality") >= 0);
        assertTrue(report.evaluationDelta().resolvedFailureCategories().contains(TuningFailureCategory.RULE_CONTRADICTION));
        assertEquals("tuned-writer-v1", report.lineageDelta().tunedModelVersion());
    }

    @Test
    void hardRegressionAndOperationalLimitBlockActivationDespiteSoftImprovement() {
        TrainingArtifact artifact = artifact();
        TuningEvaluationPort evaluator = (role, model, prompt, context, split) ->
                metrics(5.0, model.equals("tuned-writer-v1") ? 1 : 0,
                        model.equals("tuned-writer-v1") ? 99 : 10,
                        model.equals("tuned-writer-v1") ? 999 : 100);

        TuningEvaluationReport report = new TuningEvaluationService(new TuningGatePolicy(.1, 0.0, 20, 200))
                .evaluate(eligibleProposal(PromptRole.WRITER), artifact, evaluator);

        assertFalse(report.gateReport().passed());
        assertFalse(report.gateReport().hardPassed());
        assertFalse(report.gateReport().costPassed());
        assertFalse(report.gateReport().latencyPassed());
        PromptRegistry prompts = promptRegistry();
        TunedModelRegistry models = new TunedModelRegistry();
        models.registerBaseline(new RoleModelConfiguration(PromptRole.WRITER, "base-writer-v1",
                new PromptVersion(PromptRole.WRITER, "optimized-prompt-v1"), "baseline-artifact", null, null));
        assertThrows(TuningActivationException.class, () -> new TuningActivationService(prompts, models)
                .activate(report, "base-writer-v1", "operator"));
        assertEquals("base-writer-v1", models.active(PromptRole.WRITER).orElseThrow().modelVersion());
    }

    @Test
    void activationAndRollbackStayRoleScopedAndExposeLineageDelta() {
        TrainingArtifact artifact = artifact();
        TuningEvaluationPort evaluator = (role, model, prompt, context, split) ->
                metrics(model.equals("tuned-writer-v1") ? 4.0 : 3.0, 0, 11, 110);
        TuningEvaluationReport report = new TuningEvaluationService(new TuningGatePolicy(.1, 0.0, 20, 200))
                .evaluate(eligibleProposal(PromptRole.WRITER), artifact, evaluator);
        TunedModelRegistry models = new TunedModelRegistry();
        models.registerBaseline(new RoleModelConfiguration(PromptRole.WRITER, "base-writer-v1",
                new PromptVersion(PromptRole.WRITER, "optimized-prompt-v1"), "baseline-artifact", null, null));
        models.registerBaseline(new RoleModelConfiguration(PromptRole.PLANNER, "base-planner-v1",
                new PromptVersion(PromptRole.PLANNER, "planner-prompt-v1"), "planner-artifact", null, null));

        TuningActivationService service = new TuningActivationService(promptRegistry(), models);
        RoleModelConfiguration active = service.activate(report, "base-writer-v1", "operator");

        assertEquals("tuned-writer-v1", active.modelVersion());
        assertEquals("base-planner-v1", models.active(PromptRole.PLANNER).orElseThrow().modelVersion());
        RoleModelConfiguration restored = service.rollback(PromptRole.WRITER, "base-writer-v1",
                "tuned-writer-v1", "operator");
        assertEquals("base-writer-v1", restored.modelVersion());
        assertEquals(artifact.artifactId(), active.trainingArtifactId());
        assertNotNull(active.lineageDelta());
        assertTrue(models.history(PromptRole.WRITER).size() >= 3);
    }

    @Test
    void trainingArtifactAndHyperparametersSurviveJsonPersistence() throws Exception {
        Path path = Files.createTempFile("training-artifacts", ".json");
        TrainingArtifactRepository first = new TrainingArtifactRepository(new JsonTrainingArtifactStore(path));
        first.save(artifact());

        TrainingArtifactRepository restored = new TrainingArtifactRepository(new JsonTrainingArtifactStore(path));

        assertEquals(artifact(), restored.find("artifact-1").orElseThrow());
        assertEquals(42L, restored.find("artifact-1").orElseThrow().hyperparameters().seed());
    }

    @Test
    void roleActivationAndLineageSurviveJsonPersistence() throws Exception {
        Path path = Files.createTempFile("role-models", ".json");
        TunedModelRegistry first = new TunedModelRegistry(new JsonRoleModelConfigurationStore(path));
        RoleModelConfiguration baseline = new RoleModelConfiguration(PromptRole.WRITER, "base-writer-v1",
                new PromptVersion(PromptRole.WRITER, "optimized-prompt-v1"), "baseline-artifact", null, null);
        RoleModelConfiguration tuned = new RoleModelConfiguration(PromptRole.WRITER, "tuned-writer-v1",
                new PromptVersion(PromptRole.WRITER, "optimized-prompt-v1"), "artifact-1", "evaluation-1", null);
        first.registerBaseline(baseline);
        first.activate(tuned, "base-writer-v1", "operator");

        TunedModelRegistry restored = new TunedModelRegistry(new JsonRoleModelConfigurationStore(path));

        assertEquals("tuned-writer-v1", restored.active(PromptRole.WRITER).orElseThrow().modelVersion());
        assertEquals(2, restored.history(PromptRole.WRITER).size());
        assertEquals("evaluation-1", restored.history(PromptRole.WRITER).get(1).evaluationId());
    }

    private static TuningProposal eligibleProposal(PromptRole role) {
        TuningSample sample = new TuningSample("sample-1", role, "dataset-v1", DatasetSplit.TRAIN,
                "adv-1", "session-1", "scene-1", "source-1", true, true,
                List.of(FailureEvidence.resolved("sample-failure", TuningFailureCategory.RULE_CONTRADICTION)));
        return new TuningProposal("proposal-1", role, TuningMethod.SFT, "contract-v1", "eval-v1",
                "dataset-v1", "holdout-v1", "base-writer-v1", "optimized-prompt-v1", true, true,
                true, true, List.of(sample), List.of(
                FailureEvidence.resolved("failure-1", TuningFailureCategory.RULE_CONTRADICTION),
                FailureEvidence.resolved("failure-2", TuningFailureCategory.RULE_CONTRADICTION)),
                new TuningComparison(role, "base-writer-v1", "optimized-prompt-v1", "tuned-writer-v1",
                        "eval-v1", "holdout-v1", metrics(3, 0, 10, 100), metrics(4, 0, 11, 110)));
    }

    private static TuningProposal ineligibleProposal(PromptRole role) {
        TuningProposal proposal = eligibleProposal(role);
        return new TuningProposal(proposal.proposalId(), proposal.role(), proposal.method(), proposal.stableContractVersion(),
                proposal.evalVersion(), proposal.datasetVersion(), proposal.holdoutVersion(), proposal.baseModelVersion(),
                proposal.optimizedPromptVersion(), false, proposal.evalPresent(), proposal.baselinePresent(),
                proposal.optimizedPromptPresent(), List.of(), proposal.failureEvidence(), proposal.comparison());
    }

    private static TrainingArtifact artifact() {
        return new TrainingArtifact("artifact-1", "proposal-1", PromptRole.WRITER, "base-writer-v1",
                "optimized-prompt-v1", "tuned-writer-v1", "dataset-v1", "holdout-v1", hyperparameters(),
                "provider-writer-v1", "s3://training/artifact-1");
    }

    private static TrainingHyperparameters hyperparameters() {
        return new TrainingHyperparameters(TuningMethod.SFT, 3, 2, .001, 42L, Map.of("warmup", "1"));
    }

    private static TuningMetrics metrics(double quality, int ruleViolations, long costMicros, long latencyMillis) {
        return metrics(quality, ruleViolations, costMicros, latencyMillis, Set.of());
    }

    private static TuningMetrics metrics(double quality, int ruleViolations, long costMicros, long latencyMillis,
                                         Set<TuningFailureCategory> failureTaxonomy) {
        return new TuningMetrics(Map.of("rule", ruleViolations), Map.of("quality", quality), costMicros, latencyMillis,
                failureTaxonomy);
    }

    private static PromptRegistry promptRegistry() {
        PromptRegistry registry = new PromptRegistry();
        registry.registerBaseline(new PromptArtifact(new PromptVersion(PromptRole.WRITER, "optimized-prompt-v1"), null,
                "writer prompt", "schema", List.of("context"), "after-context", "base-writer-v1", "config-v1",
                "dataset-v1", "eval-v1", true, PromptArtifactStatus.DRAFT));
        return registry;
    }

    private static final class CountingTrainer implements RoleTuningPort {
        private final PromptRole role;
        private final AtomicInteger calls = new AtomicInteger();

        private CountingTrainer(PromptRole role) { this.role = role; }
        @Override public PromptRole role() { return role; }
        @Override public TrainingArtifact train(TuningTrainingRequest request) {
            calls.incrementAndGet();
            return artifact();
        }
    }

    private record EvaluationCall(PromptRole role, String model, String prompt,
                                  TuningEvaluationContext context, DatasetSplit split) {}
}
