package com.dndmaster.aigamemaster.benchmark;

public record GmBenchmarkTiming(double ttftMs, double completionMs, double retrievalMs,
                                double repairInclusiveMs, double endToEndMs) {
    public GmBenchmarkTiming {
        if (!valid(ttftMs) || !valid(completionMs) || !valid(retrievalMs)
                || !valid(repairInclusiveMs) || !valid(endToEndMs)) {
            throw new IllegalArgumentException("invalid benchmark phase timing");
        }
    }

    public static GmBenchmarkTiming fromEndToEnd(double latencyMs) {
        return new GmBenchmarkTiming(latencyMs, latencyMs, 0, latencyMs, latencyMs);
    }

    private static boolean valid(double value) { return Double.isFinite(value) && value >= 0; }
}
