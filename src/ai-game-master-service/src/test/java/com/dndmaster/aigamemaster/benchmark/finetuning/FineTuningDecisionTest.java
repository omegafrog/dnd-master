package com.dndmaster.aigamemaster.benchmark.finetuning;

import static org.junit.jupiter.api.Assertions.*;

import com.dndmaster.aigamemaster.benchmark.GmBenchmarkConfig;
import com.dndmaster.aigamemaster.benchmark.GmBenchmarkCase;
import com.dndmaster.aigamemaster.benchmark.rag.RagAbCase;
import com.dndmaster.aigamemaster.infrastructure.ai.GmProviderRequest;
import java.util.List;
import org.junit.jupiter.api.Test;

class FineTuningDecisionTest {
    private static final GmBenchmarkCase CASE = new GmBenchmarkCase("case-01", "open door", List.of("rules#door"), List.of("hidden key"));
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
    void split_rejects_validation_or_holdout_identity_and_content_digest_leakage() {
        assertThrows(IllegalArgumentException.class, () -> new FineTuningDatasetSplit(
                "split-v2", List.of("train"), List.of("holdout"), List.of("holdout"),
                "sha256:train", "sha256:validation", "sha256:holdout"));
        assertThrows(IllegalArgumentException.class, () -> new FineTuningDatasetSplit(
                "split-v2", List.of("train"), List.of("validation"), List.of("holdout"),
                "sha256:same", "sha256:same", "sha256:holdout"));
        assertThrows(IllegalArgumentException.class, () -> new FineTuningDatasetSplit(
                "split-v2", List.of("train"), List.of("validation"), List.of("holdout"),
                "sha256:train", "sha256:validation", "sha256:holdout",
                List.of("content-a"), List.of("content-b"), List.of("content-a")));
    }

    @Test
    void decision_rejects_fine_tuned_secret_regression() {
        var split = new FineTuningDatasetSplit("split-v1", List.of("a"), List.of("b"),
                "sha256:train", "sha256:test");
        var base = artifact(FineTuningModelArtifact.Variant.BASE, "base-digest");
        var tuned = artifact(FineTuningModelArtifact.Variant.FINE_TUNED, "tuned-digest");
        var evaluations = new java.util.ArrayList<FineTuningEvaluation>();
        for (var condition : RagCondition.values()) {
            evaluations.add(evaluation(base, condition, 1, 0));
            evaluations.add(evaluation(tuned, condition, 1.1, .1));
        }
        assertEquals(FineTuningDecisionReport.Decision.NO_GO,
                FineTuningDecisionReport.create(split, evaluations).decision());
    }

    @Test
    void runner_evaluates_complete_holdout_corpus_for_each_artifact_and_condition() {
        var split = new FineTuningDatasetSplit("split-v2", List.of("train"), List.of("validation"),
                List.of("case-01", "case-02"), "sha256:train", "sha256:validation", "sha256:holdout");
        var base = artifact(FineTuningModelArtifact.Variant.BASE, "base-digest", "split-v2");
        var tuned = artifact(FineTuningModelArtifact.Variant.FINE_TUNED, "tuned-digest", "split-v2");
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        var report = new FineTuningEvaluationRunner().run(split,
                List.of(new RagAbCase(CASE, List.of("rules#similar-door")),
                        new RagAbCase(new GmBenchmarkCase("case-02", "close door", List.of("rules#door"), List.of("hidden key")),
                                List.of("rules#similar-door"))),
                c -> List.of("rules#similar-door"), base, tuned, CONFIG,
                (artifact, condition, ignoredSplit, ignoredConfig, evidence) -> {
                    calls.incrementAndGet();
                    return new FineTuningMetrics(1, .5, .5, .5, 100, 25, 1, 0, 3);
                });
        assertEquals(16, calls.get());
        assertEquals(FineTuningDecisionReport.Decision.NO_GO, report.decision());
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
                evaluation(base, RagCondition.ORACLE, 3), evaluation(base, RagCondition.DISTRACTOR, 3),
                evaluation(tuned, RagCondition.NO_RAG, 3), evaluation(tuned, RagCondition.CURRENT_RAG, 3),
                evaluation(tuned, RagCondition.ORACLE, 3), evaluation(tuned, RagCondition.DISTRACTOR, 3));
        var report = FineTuningDecisionReport.create(split, evaluations);
        assertEquals(FineTuningDecisionReport.Decision.NO_GO, report.decision());
        assertTrue(report.rationale().contains("quality"));
        assertThrows(IllegalArgumentException.class, () -> new FineTuningDecisionReport(
                "gm-quality-finetuning.v1", split, evaluations, FineTuningDecisionReport.Decision.GO, "bypass"));
    }

    @Test
    void runner_executes_each_artifact_under_each_rag_condition() {
        var split = new FineTuningDatasetSplit("split-v1", List.of("a"), List.of("case-01"), "sha256:train", "sha256:test");
        var base = artifact(FineTuningModelArtifact.Variant.BASE, "base-digest");
        var tuned = artifact(FineTuningModelArtifact.Variant.FINE_TUNED, "tuned-digest");
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        var report = new FineTuningEvaluationRunner().run(split,
                new RagAbCase(CASE, List.of("rules#similar-door")), c -> List.of("rules#similar-door"), base, tuned, CONFIG,
                (artifact, condition, ignoredSplit, ignoredConfig, evidence) -> {
                    calls.incrementAndGet();
                    return new FineTuningMetrics(1, .5, .5, .5, 100, 25, 1, 0, 3);
                });
        assertEquals(8, calls.get());
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
        return artifact(variant, digest, "split-v1");
    }

    private static FineTuningModelArtifact artifact(FineTuningModelArtifact.Variant variant, String digest, String split) {
        return new FineTuningModelArtifact(variant, new GmProviderRequest("ollama", "model", "medium"),
                digest, split, split.equals("split-v2") ? "sha256:train" : "sha256:train");
    }

    private static FineTuningEvaluation evaluation(FineTuningModelArtifact artifact, RagCondition condition,
                                                   double quality) {
        return evaluation(artifact, condition, quality, 0);
    }

    private static FineTuningEvaluation evaluation(FineTuningModelArtifact artifact, RagCondition condition,
                                                   double quality, double secretLeakRate) {
        return new FineTuningEvaluation(artifact, condition, CONFIG,
                new FineTuningMetrics(quality, .5, .5, .5, 100, 25, 1, 0, 3,
                        secretLeakRate, 1, 1, java.util.Collections.nCopies(3, quality)));
    }
}
