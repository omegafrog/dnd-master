package com.dndmaster.aigamemaster.benchmark.rag;

public record RagAbAnalysis(RagAbBottleneck bottleneck, boolean ragImprovementAccepted, double currentVsNoRagDelta, double oracleVsCurrentDelta,
        String rationale, String testMethod, double effectSize, double pValue, double confidenceLow, double confidenceHigh,
        int excludedRuns) {
    public RagAbAnalysis(RagAbBottleneck bottleneck, boolean accepted, double currentDelta,
                         double oracleDelta, String rationale) {
        this(bottleneck, accepted, currentDelta, oracleDelta, rationale, "legacy", currentDelta, 1.0, currentDelta, currentDelta, 0);
    }
}
