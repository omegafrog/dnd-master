package com.dndmaster.aigamemaster.benchmark.rag;

import static org.junit.jupiter.api.Assertions.*;

import com.dndmaster.aigamemaster.benchmark.GmBenchmarkCase;
import com.dndmaster.aigamemaster.benchmark.GmBenchmarkConfig;
import java.util.List;
import org.junit.jupiter.api.Test;

class RagAbEvaluationTest {
    private static final GmBenchmarkCase CASE = new GmBenchmarkCase(
            "case-01", "open door", List.of("rules#door"), List.of("hidden key"));

    @Test
    void providers_are_isolated_and_preserve_case_and_generation_configuration() {
        var expected = List.of("rules#door");
        var distractors = List.of("rules#similar-door");
        assertEquals(List.of(), new NoRagEvidenceProvider().evidence(CASE));
        assertEquals(expected, new OracleRagEvidenceProvider().evidence(CASE));
        assertEquals(distractors, new DistractorRagEvidenceProvider().evidence(
                new RagAbCase(CASE, distractors)));

        var config = new GmBenchmarkConfig("gm-baseline-v1", "qwen", "sha256:x", .2, 512, 4096, 3);
        var run = new RagAbRunner(new CurrentRagEvidenceProvider(c -> expected)).run(new RagAbCorpus("gm-baseline-v1", List.of(new RagAbCase(CASE, distractors))),
                config, (c, condition, supplied, unchanged) ->
                        new RagAbExecution(true, true, true, false, false, true, true, 4.0, 4.0, "raw"));
        assertEquals(config, run.configuration());
        assertEquals(4, run.conditions().size());
        assertEquals(4, run.conditions().stream().map(RagAbConditionReport::condition).distinct().count());
    }

    @Test
    void report_contains_all_conditions_and_classifies_bottleneck() {
        var corpus = new RagAbCorpus("gm-baseline-v1", List.of(new RagAbCase(CASE, List.of("rules#similar-door"))));
        var config = new GmBenchmarkConfig("gm-baseline-v1", "qwen", "sha256:x", .2, 512, 4096, 3);
        var report = new RagAbRunner(new CurrentRagEvidenceProvider(c -> List.of("rules#similar-door"))).run(corpus, config, (c, condition, evidence, unchanged) -> {
            boolean good = condition == RagAbCondition.ORACLE;
            return new RagAbExecution(good, good, good, !good, false, good, good, good ? 5.0 : 1.0, good ? 5.0 : 20.0, "raw");
        });
        assertEquals(RagAbCondition.values().length, report.conditions().size());
        assertEquals(RagAbBottleneck.RETRIEVAL, report.analysis().bottleneck());
        assertTrue(report.conditions().stream().allMatch(c -> c.metrics().runs() == 3));
        assertEquals(0.0, report.conditions().stream().filter(c -> c.condition() == RagAbCondition.NO_RAG)
                .findFirst().orElseThrow().metrics().retrievalRecallMean());
        assertEquals(1.0, report.conditions().stream().filter(c -> c.condition() == RagAbCondition.ORACLE)
                .findFirst().orElseThrow().metrics().retrievalRecallMean());
    }

    @Test
    void conditions_cannot_mutate_shared_case_or_config() {
        var corpus = new RagAbCorpus("gm-baseline-v1", List.of(new RagAbCase(CASE, List.of("rules#wrong"))));
        var config = new GmBenchmarkConfig("gm-baseline-v1", "qwen", "sha256:x", .2, 512, 4096, 3);
        assertThrows(IllegalArgumentException.class, () -> new RagAbRunner(new CurrentRagEvidenceProvider(c -> c.expectedEvidence())).run(corpus,
                new GmBenchmarkConfig("other", "qwen", "sha256:x", .2, 512, 4096, 3),
                (c, condition, evidence, unchanged) -> new RagAbExecution(true, true, true, false, false, true, true, 1, 1, "")));
    }

    @Test
    void paired_analysis_reports_effect_confidence_and_significance() {
        var result = RagAbPairedStatistics.analyze(List.of(1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0),
                List.of(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0), 0.05, 0L);

        assertEquals(1.0, result.effect());
        assertTrue(result.confidenceLow() > 0.0);
        assertTrue(result.confidenceHigh() >= result.confidenceLow());
        assertTrue(result.pValue() <= 0.05);
        assertTrue(result.significant());
    }

    @Test
    void reviewer_records_are_blind_and_validate_score_and_provenance() {
        var record = new RagAbReviewerRecord("case-01", RagAbCondition.CURRENT_RAG, 0,
                "reviewer-1", 4.0, "blind-web-2026-01", "response-hash");

        assertEquals("reviewer-1", record.reviewerId());
        assertEquals(4.0, record.score());
        assertThrows(IllegalArgumentException.class, () -> new RagAbReviewerRecord(
                "case-01", RagAbCondition.CURRENT_RAG, 0, "", 4.0, "blind-web", "hash"));
        assertThrows(IllegalArgumentException.class, () -> new RagAbReviewerRecord(
                "case-01", RagAbCondition.CURRENT_RAG, 0, "reviewer", 6.0, "blind-web", "hash"));
    }
}
