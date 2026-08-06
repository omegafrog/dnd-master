package com.dndmaster.aigamemaster.benchmark;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.nio.file.Files;
import org.junit.jupiter.api.Test;

class GmBenchmarkBaselineTest {
    @Test
    void configuration_requires_model_identity_and_runtime_limits() {
        assertThrows(IllegalArgumentException.class, () -> new GmBenchmarkConfig(
                "gm-baseline-v1", "qwen3:8b", "", 0.2, 512, 4096, 3));
        assertThrows(IllegalArgumentException.class, () -> new GmBenchmarkConfig(
                "gm-baseline-v1", "qwen3:8b", "sha256:abc", Double.NaN, 512, 4096, 3));
        assertThrows(IllegalArgumentException.class, () -> new GmBenchmarkConfig(
                "gm-baseline-v1", "qwen3:8b", "sha256:abc", 0.2, 0, 4096, 2));
    }

    @Test
    void corpus_has_stable_identity_and_schema() {
        var corpus = new GmBenchmarkCorpus("gm-baseline-v1", List.of(
                new GmBenchmarkCase("case-01", "open door", List.of("rules#door"), List.of("hidden key"))));

        assertEquals("gm-baseline-v1:case-01", corpus.caseIdentity(corpus.cases().getFirst()));
        assertThrows(IllegalArgumentException.class, () -> new GmBenchmarkCorpus(
                "gm-baseline-v1", List.of(corpus.cases().getFirst(), corpus.cases().getFirst())));
    }

    @Test
    void aggregation_returns_repeat_metrics_and_rejects_non_finite_values() {
        var runs = List.of(
                new GmBenchmarkRun("case-01", 0, GmBenchmarkRun.TemperatureState.COLD,
                        "raw-1", true, false, true, 10),
                new GmBenchmarkRun("case-01", 1, GmBenchmarkRun.TemperatureState.WARM,
                        "raw-2", false, false, true, 30),
                new GmBenchmarkRun("case-01", 2, GmBenchmarkRun.TemperatureState.WARM,
                        "raw-3", true, true, false, 20));

        var metrics = GmBenchmarkAggregator.aggregate(runs);
        assertEquals(20.0, metrics.latencyMeanMs());
        assertEquals(100.0, metrics.latencyVarianceMs());
        assertEquals(20.0, metrics.latencyP50Ms());
        assertEquals(30.0, metrics.latencyP95Ms());
        assertEquals(2.0 / 3.0, metrics.structureSuccessRate());
        assertEquals(1.0 / 3.0, metrics.leakRate());
        assertEquals(2.0 / 3.0, metrics.citationRate());
        assertThrows(IllegalArgumentException.class, () -> GmBenchmarkAggregator.aggregate(List.of(
                new GmBenchmarkRun("case-01", 0, GmBenchmarkRun.TemperatureState.COLD,
                        "raw", true, false, true, Double.NaN))));
    }

    @Test
    void runner_emits_three_runs_per_frozen_case_and_raw_artifacts() throws Exception {
        var corpus = new GmBenchmarkCorpus("gm-baseline-v1", List.of(
                new GmBenchmarkCase("case-01", "open door", List.of("rules#door"), List.of("hidden key"))));
        var config = new GmBenchmarkConfig("gm-baseline-v1", "qwen3:8b", "sha256:abc", 0.2, 512, 4096, 3);
        var report = new GmBenchmarkRunner().run(corpus, config,
                (benchmarkCase, ignored, state) -> new GmBenchmarkExecution(
                        "raw-" + state, true, false, true, state == GmBenchmarkRun.TemperatureState.COLD ? 10 : 20));

        assertEquals("gm-quality-baseline.v1", report.schemaVersion());
        assertEquals(3, report.runs().size());
        var directory = Files.createTempDirectory("gm-baseline");
        new GmBenchmarkArtifactStore(new com.fasterxml.jackson.databind.ObjectMapper()).write(directory, report);
        assertEquals(3, Files.list(directory.resolve("raw")).count());
        assertTrue(Files.exists(directory.resolve("baseline-report.json")));
    }

    @Test
    void frozen_resource_contains_exactly_thirty_cases() throws Exception {
        var corpus = GmBenchmarkCorpusLoader.load(
                getClass().getResourceAsStream("/gm-quality-baseline-corpus.json"),
                new com.fasterxml.jackson.databind.ObjectMapper());
        assertEquals(30, corpus.cases().size());
        assertEquals("gm-baseline-v1", corpus.version());
    }
}
