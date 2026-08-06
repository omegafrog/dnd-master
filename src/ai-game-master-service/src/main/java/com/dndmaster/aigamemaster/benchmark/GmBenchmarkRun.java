package com.dndmaster.aigamemaster.benchmark;

public record GmBenchmarkRun(String caseId, int runIndex, TemperatureState temperatureState,
                             String rawResponse, boolean structuredSuccess, boolean secretLeak,
                             boolean citationCorrect, double latencyMs) {
    public enum TemperatureState { COLD, WARM }

    public GmBenchmarkRun {
        if (caseId == null || caseId.isBlank() || runIndex < 0 || temperatureState == null
                || rawResponse == null || !Double.isFinite(latencyMs) || latencyMs < 0) {
            throw new IllegalArgumentException("invalid benchmark run");
        }
    }
}
