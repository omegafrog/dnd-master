package com.dndmaster.aigamemaster.benchmark.rag;

public record RagAbExecution(boolean structuredSuccess, boolean ruleAccurate, boolean citationCorrect,
        boolean hallucination, boolean secretLeak, boolean prematureStateChange, boolean continuityCorrect,
        double humanScore, double latencyMs, String rawResponse) {
    public RagAbExecution {
        if (!Double.isFinite(humanScore) || humanScore < 0 || humanScore > 5
                || !Double.isFinite(latencyMs) || latencyMs < 0 || rawResponse == null) {
            throw new IllegalArgumentException("invalid RAG A/B execution");
        }
    }
}
