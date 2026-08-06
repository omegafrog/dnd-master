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
        var report = new RagAbRunner(new CurrentRagEvidenceProvider(c -> c.expectedEvidence())).run(corpus, config, (c, condition, evidence, unchanged) -> {
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
}
