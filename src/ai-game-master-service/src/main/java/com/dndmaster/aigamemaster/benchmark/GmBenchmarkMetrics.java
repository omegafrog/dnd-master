package com.dndmaster.aigamemaster.benchmark;

public record GmBenchmarkMetrics(int runs, double structureSuccessRate, double leakRate,
                                 double citationRate, double latencyMeanMs, double latencyVarianceMs,
                                 double latencyP50Ms, double latencyP95Ms) {}
