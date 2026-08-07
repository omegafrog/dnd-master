package com.dndmaster.aigamemaster.benchmark;

import java.util.List;

public record GmBenchmarkReport(String schemaVersion, String corpusVersion, String model,
                                String modelDigest, double temperature, int tokenCap, int contextSize,
                                List<GmBenchmarkRun> runs, List<GmBenchmarkCaseMetrics> cases,
                                GmBenchmarkMetrics overallMetrics, GmBenchmarkMetrics coldMetrics,
                                GmBenchmarkMetrics warmMetrics, GmLatencyMetadata latencyMetadata) {
    public GmBenchmarkReport {
        if (!"gm-quality-baseline.v1".equals(schemaVersion)) {
            throw new IllegalArgumentException("unsupported benchmark schema version");
        }
        runs = List.copyOf(runs);
        cases = List.copyOf(cases);
    }

    public GmBenchmarkReport(String schemaVersion, String corpusVersion, String model, String modelDigest,
                             double temperature, int tokenCap, int contextSize, List<GmBenchmarkRun> runs,
                             List<GmBenchmarkCaseMetrics> cases, GmBenchmarkMetrics overallMetrics,
                             GmBenchmarkMetrics coldMetrics, GmBenchmarkMetrics warmMetrics) {
        this(schemaVersion, corpusVersion, model, modelDigest, temperature, tokenCap, contextSize, runs, cases,
                overallMetrics, coldMetrics, warmMetrics,
                null);
    }

    public void assertPublishable() {
        if (latencyMetadata == null) throw new IllegalStateException("latency deadline metadata required");
        if (latencyMetadata.sampleCount() < 3 || latencyMetadata.sampleCount() != runs.size()) {
            throw new IllegalStateException("benchmark sample count is inadequate or inconsistent");
        }
        var endToEnd = overallMetrics.phaseMetrics().get(GmBenchmarkPhase.END_TO_END);
        if (endToEnd == null || endToEnd.p95Ms() > latencyMetadata.totalDeadlineMs()) {
            throw new IllegalStateException("benchmark end-to-end p95 exceeds declared deadline");
        }
    }

    public record GmBenchmarkCaseMetrics(String caseId, GmBenchmarkMetrics metrics) {}
}
