package com.dndmaster.gmeval;

import static org.junit.jupiter.api.Assertions.*;

import com.dndmaster.gmeval.optimization.*;
import com.dndmaster.gmeval.registry.*;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class GatedPromptEvaluationTest {
    @Test
    void hardRegressionRejectsEvenWhenSoftQualityImproves() {
        PromptCandidateGate gate = new PromptCandidateGate();
        MetricVector baseline = metrics(0, 0, 0, 0, 3.0);
        MetricVector candidate = metrics(1, 0, 0, 0, 5.0);

        GateDecision decision = gate.evaluate(candidate, baseline);

        assertFalse(decision.accepted());
        assertEquals(List.of(HardMetric.RULE_VIOLATION), decision.regressions());
    }

    @Test
    void validCandidatesUseStableSoftScoreThenCandidateIdTieBreak() {
        PromptCandidateGate gate = new PromptCandidateGate();
        MetricVector baseline = metrics(0, 0, 0, 0, 3.0);
        PromptCandidate first = candidate("candidate-b", PromptRole.WRITER, "1.1.0", 7);
        PromptCandidate second = candidate("candidate-a", PromptRole.WRITER, "1.2.0", 7);

        PromptCandidateEvaluation selected = gate.selectBest(List.of(
                evaluation(first, metrics(0, 0, 0, 0, 4.0), baseline),
                evaluation(second, metrics(0, 0, 0, 0, 4.0), baseline)));

        assertEquals("candidate-a", selected.candidate().candidateId());
    }

    @Test
    void candidateCannotEvaluateAnotherRole() {
        PromptArtifact baseline = artifact(PromptRole.WRITER, "1.0.0", true);
        PromptCandidate planner = candidate("planner", PromptRole.PLANNER, "1.1.0", 7);

        assertThrows(IllegalArgumentException.class, () -> new PromptOptimizationRun(
                "run-1", PromptRole.WRITER, "dataset-v1", "eval-v1", 7,
                baseline, List.of(evaluation(planner, metrics(0, 0, 0, 0, 4.0), metrics(0, 0, 0, 0, 3.0))),
                null));
    }

    @Test
    void searchUsesTrainAndSelectionUsesDevButNeverHoldout() {
        List<DatasetCaseRef> train = List.of(ref("train", DatasetSplit.TRAIN));
        List<DatasetCaseRef> dev = List.of(ref("dev", DatasetSplit.DEV));

        assertDoesNotThrow(() -> DatasetSplitPolicy.validateForPhase(train, OptimizationPhase.SEARCH));
        assertDoesNotThrow(() -> DatasetSplitPolicy.validateForPhase(dev, OptimizationPhase.SELECTION));
        assertThrows(IllegalArgumentException.class,
                () -> DatasetSplitPolicy.validateForPhase(dev, OptimizationPhase.SEARCH));
        assertThrows(IllegalArgumentException.class,
                () -> DatasetSplitPolicy.validateForPhase(List.of(ref("holdout", DatasetSplit.HOLDOUT)), OptimizationPhase.SELECTION));
    }

    @Test
    void runnerPersistsRepeatableReportAndUsesDevForSelection() {
        PromptArtifact baseline = artifact(PromptRole.WRITER, "1.0.0", true);
        PromptCandidate candidate = candidate("candidate-a", PromptRole.WRITER, "1.1.0", 7);
        PromptOptimizationRunRepository repository = new PromptOptimizationRunRepository(new InMemoryPromptOptimizationRunStore());
        PromptOptimizationRequest request = new PromptOptimizationRequest("run-repeatable", PromptRole.WRITER,
                "dataset-v1", "eval-v1", 7, baseline, List.of(candidate),
                List.of(ref("train", DatasetSplit.TRAIN)), List.of(ref("dev", DatasetSplit.DEV)), Map.of());

        PromptOptimizationRun first = new PromptOptimizationRunner(repository).run(request,
                (value, cases, phase) -> phase == OptimizationPhase.SEARCH
                        ? metrics(0, 0, 0, 0, 1.0) : metrics(0, 0, 0, 0, 5.0));
        PromptOptimizationRun second = new PromptOptimizationRunner(repository).run(request,
                (value, cases, phase) -> phase == OptimizationPhase.SEARCH
                        ? metrics(0, 0, 0, 0, 1.0) : metrics(0, 0, 0, 0, 5.0));

        assertEquals(first.report().reportFingerprint(), second.report().reportFingerprint());
        assertEquals("candidate-a", repository.readProjection("run-repeatable").selectedCandidateId());
    }

    @Test
    void runnerRejectsInvalidDatasetBeforeEvaluator() {
        PromptArtifact baseline = artifact(PromptRole.WRITER, "1.0.0", true);
        PromptCandidate candidate = candidate("candidate-a", PromptRole.WRITER, "1.1.0", 7);
        PromptOptimizationRequest request = new PromptOptimizationRequest("run-invalid", PromptRole.WRITER,
                "dataset-v1", "eval-v1", 7, baseline, List.of(candidate),
                List.of(ref("same", DatasetSplit.TRAIN), ref("leak", DatasetSplit.DEV)),
                List.of(ref("dev", DatasetSplit.DEV)), Map.of());
        PromptOptimizationRunRepository repository = new PromptOptimizationRunRepository(new InMemoryPromptOptimizationRunStore());

        assertThrows(IllegalArgumentException.class, () -> new PromptOptimizationRunner(repository).run(request,
                (value, cases, phase) -> { throw new AssertionError("evaluator must not run"); }));
    }

    @Test
    void runnerPersistsRejectedCandidatesWithoutSelectingOne() {
        PromptArtifact baseline = artifact(PromptRole.WRITER, "1.0.0", true);
        PromptCandidate candidate = candidate("unsafe", PromptRole.WRITER, "1.1.0", 7);
        PromptOptimizationRunRepository repository = new PromptOptimizationRunRepository(new InMemoryPromptOptimizationRunStore());
        PromptOptimizationRequest request = new PromptOptimizationRequest("run-rejected", PromptRole.WRITER,
                "dataset-v1", "eval-v1", 7, baseline, List.of(candidate),
                List.of(ref("train", DatasetSplit.TRAIN)), List.of(ref("dev", DatasetSplit.DEV)), Map.of());

        PromptOptimizationRun run = new PromptOptimizationRunner(repository).run(request,
                (value, cases, phase) -> metrics(value.candidateId().equals("unsafe") ? 1 : 0, 0, 0, 0, 5.0));

        assertNull(run.selected());
        assertEquals("unsafe", repository.readProjection("run-rejected").candidates().getFirst().candidate().candidateId());
        assertFalse(repository.readProjection("run-rejected").candidates().getFirst().gate().accepted());
    }

    private static PromptCandidateEvaluation evaluation(PromptCandidate candidate, MetricVector metrics, MetricVector baseline) {
        return PromptCandidateEvaluation.from(candidate, metrics, BaselineDelta.between(metrics, baseline),
                List.of("representative output"));
    }

    private static MetricVector metrics(int rule, int secret, int agency, int schema, double quality) {
        return new MetricVector(Map.of(
                HardMetric.RULE_VIOLATION, rule,
                HardMetric.SECRET_LEAK, secret,
                HardMetric.AGENCY_VIOLATION, agency,
                HardMetric.SCHEMA_FAILURE, schema),
                Map.of("quality", quality));
    }

    private static PromptCandidate candidate(String id, PromptRole role, String version, long seed) {
        return new PromptCandidate(id, artifact(role, version, false), seed);
    }

    private static PromptArtifact artifact(PromptRole role, String version, boolean baseline) {
        return new PromptArtifact(new PromptVersion(role, version), null, "prompt-" + version,
                "schema-1", List.of("context"), "after-context", "model-1", "config-1",
                "dataset-v1", "eval-v1", baseline, PromptArtifactStatus.DRAFT);
    }

    private static DatasetCaseRef ref(String id, DatasetSplit split) {
        return new DatasetCaseRef(id, "dataset-v1", split, "adventure-" + id, "scene-" + id);
    }
}
