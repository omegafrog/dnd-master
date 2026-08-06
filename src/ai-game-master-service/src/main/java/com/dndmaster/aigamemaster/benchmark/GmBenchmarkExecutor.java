package com.dndmaster.aigamemaster.benchmark;

@FunctionalInterface
public interface GmBenchmarkExecutor {
    GmBenchmarkExecution execute(GmBenchmarkCase benchmarkCase, GmBenchmarkConfig config,
                                 GmBenchmarkRun.TemperatureState temperatureState);
}
