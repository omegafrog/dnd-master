package com.dndmaster.aigamemaster.benchmark;

import java.util.List;

public record GmBenchmarkReport(String schemaVersion, String corpusVersion, String model,
                                String modelDigest, double temperature, int tokenCap, int contextSize,
                                List<GmBenchmarkRun> runs, List<GmBenchmarkCaseMetrics> cases,
                                GmBenchmarkMetrics overallMetrics) {
    public GmBenchmarkReport {
        schemaVersion = "gm-quality-baseline.v1";
        runs = List.copyOf(runs);
        cases = List.copyOf(cases);
    }

    public record GmBenchmarkCaseMetrics(String caseId, GmBenchmarkMetrics metrics) {}
}
