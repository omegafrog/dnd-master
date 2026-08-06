package com.dndmaster.aigamemaster.benchmark.rag;

public record RagAbAnalysis(RagAbBottleneck bottleneck, boolean ragImprovementAccepted, double currentVsNoRagDelta, double oracleVsCurrentDelta,
        String rationale) {}
