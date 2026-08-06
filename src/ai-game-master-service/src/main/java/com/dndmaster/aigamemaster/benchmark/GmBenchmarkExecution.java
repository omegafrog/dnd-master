package com.dndmaster.aigamemaster.benchmark;

public record GmBenchmarkExecution(String rawResponse, boolean structuredSuccess, boolean secretLeak,
                                    boolean citationCorrect, double latencyMs) {}
