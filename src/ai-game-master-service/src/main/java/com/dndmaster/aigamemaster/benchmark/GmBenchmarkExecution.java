package com.dndmaster.aigamemaster.benchmark;

public record GmBenchmarkExecution(String rawResponse, boolean structuredSuccess, boolean secretLeak,
                                    boolean citationCorrect, double latencyMs, GmBenchmarkTiming timing) {
    public GmBenchmarkExecution(String rawResponse, boolean structuredSuccess, boolean secretLeak,
                                 boolean citationCorrect, double latencyMs) {
        this(rawResponse, structuredSuccess, secretLeak, citationCorrect, latencyMs,
                GmBenchmarkTiming.fromEndToEnd(latencyMs));
    }
}
