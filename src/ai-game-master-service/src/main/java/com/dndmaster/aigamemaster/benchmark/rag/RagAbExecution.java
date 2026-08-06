package com.dndmaster.aigamemaster.benchmark.rag;

public record RagAbExecution(boolean structuredSuccess, boolean ruleAccurate, boolean citationCorrect,
        boolean hallucination, boolean secretLeak, boolean prematureStateChange, boolean continuityCorrect,
        double humanScore, double latencyMs, String rawResponse, double retrievalRecall) {
    public RagAbExecution(boolean structuredSuccess, boolean ruleAccurate, boolean citationCorrect,
            boolean hallucination, boolean secretLeak, boolean prematureStateChange, boolean continuityCorrect,
            double humanScore, double latencyMs, String rawResponse) {
        this(structuredSuccess, ruleAccurate, citationCorrect, hallucination, secretLeak, prematureStateChange,
                continuityCorrect, humanScore, latencyMs, rawResponse, 0);
    }
    public RagAbExecution {
        if (!Double.isFinite(humanScore) || humanScore < 0 || humanScore > 5
                || !Double.isFinite(latencyMs) || latencyMs < 0 || rawResponse == null
                || !Double.isFinite(retrievalRecall) || retrievalRecall < 0 || retrievalRecall > 1) {
            throw new IllegalArgumentException("invalid RAG A/B execution");
        }
    }
    public RagAbExecution withRetrievalRecall(double recall) {
        return new RagAbExecution(structuredSuccess, ruleAccurate, citationCorrect, hallucination, secretLeak,
                prematureStateChange, continuityCorrect, humanScore, latencyMs, rawResponse, recall);
    }
}
