package com.dndmaster.aigamemaster.benchmark.finetuning;

import static org.junit.jupiter.api.Assertions.*;

import com.dndmaster.aigamemaster.benchmark.GmBenchmarkConfig;
import com.dndmaster.aigamemaster.infrastructure.ai.GmProviderRequest;
import java.util.List;
import org.junit.jupiter.api.Test;

class FineTuningDecisionTest {
    private static final GmBenchmarkConfig CONFIG = new GmBenchmarkConfig(
            "gm-finetune-v1", "model", "sha256:config", .2, 512, 4096, 3);

    @Test
    void split_rejects_test_leakage_and_artifact_requires_identity() {
        assertThrows(IllegalArgumentException.class, () -> new FineTuningDatasetSplit(
                "split-v1", List.of("a", "b"), List.of("b"), "sha256:train", "sha256:test"));
        assertThrows(IllegalArgumentException.class, () -> new FineTuningModelArtifact(
                FineTuningModelArtifact.Variant.FINE_TUNED,
                new GmProviderRequest("ollama", "model", "medium"), "", "split-v1", "sha256:train"));
    }

    @Test
    void decision_requires_complete_identical_matrix_and_records_no_go() {
        var split = new FineTuningDatasetSplit("split-v1", List.of("a"), List.of("b"),
                "sha256:train", "sha256:test");
        var base = artifact(FineTuningModelArtifact.Variant.BASE, "base-digest");
        var tuned = artifact(FineTuningModelArtifact.Variant.FINE_TUNED, "tuned-digest");
        assertThrows(IllegalArgumentException.class, () -> FineTuningDecisionReport.create(
                split, List.of(evaluation(base, RagCondition.NO_RAG, 1), evaluation(tuned, RagCondition.NO_RAG, 1))));

        var evaluations = List.of(
                evaluation(base, RagCondition.NO_RAG, 3), evaluation(base, RagCondition.CURRENT_RAG, 3),
                evaluation(base, RagCondition.ORACLE, 3), evaluation(tuned, RagCondition.NO_RAG, 3),
                evaluation(tuned, RagCondition.CURRENT_RAG, 3), evaluation(tuned, RagCondition.ORACLE, 3));
        var report = FineTuningDecisionReport.create(split, evaluations);
        assertEquals(FineTuningDecisionReport.Decision.NO_GO, report.decision());
        assertTrue(report.rationale().contains("quality"));
        assertThrows(IllegalArgumentException.class, () -> new FineTuningDecisionReport(
                "gm-quality-finetuning.v1", split, evaluations, FineTuningDecisionReport.Decision.GO, "bypass"));
    }

    @Test
    void runner_executes_each_artifact_under_each_rag_condition() {
        var split = new FineTuningDatasetSplit("split-v1", List.of("a"), List.of("b"), "sha256:train", "sha256:test");
        var base = artifact(FineTuningModelArtifact.Variant.BASE, "base-digest");
        var tuned = artifact(FineTuningModelArtifact.Variant.FINE_TUNED, "tuned-digest");
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        var report = new FineTuningEvaluationRunner().run(split, base, tuned, CONFIG,
                (artifact, condition, ignoredSplit, ignoredConfig) -> {
                    calls.incrementAndGet();
                    return new FineTuningMetrics(1, .5, .5, .5, 100, 25, 1);
                });
        assertEquals(6, calls.get());
        assertEquals(FineTuningDecisionReport.Decision.NO_GO, report.decision());
    }

    @Test
    void artifact_metadata_round_trips_provider_neutral_configuration() throws Exception {
        var artifact = artifact(FineTuningModelArtifact.Variant.FINE_TUNED, "tuned-digest");
        var directory = java.nio.file.Files.createTempDirectory("fine-tuned-artifact");
        var store = new FineTuningArtifactStore(new com.fasterxml.jackson.databind.ObjectMapper());
        assertEquals(artifact, store.read(store.write(directory, artifact)));
    }

    private static FineTuningModelArtifact artifact(FineTuningModelArtifact.Variant variant, String digest) {
        return new FineTuningModelArtifact(variant, new GmProviderRequest("ollama", "model", "medium"),
                digest, "split-v1", "sha256:train");
    }

    private static FineTuningEvaluation evaluation(FineTuningModelArtifact artifact, RagCondition condition,
                                                   double quality) {
        return new FineTuningEvaluation(artifact, condition, CONFIG,
                new FineTuningMetrics(quality, .5, .5, .5, 100, 25, 1));
    }
}
