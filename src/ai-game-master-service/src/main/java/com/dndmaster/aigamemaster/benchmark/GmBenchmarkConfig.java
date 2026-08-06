package com.dndmaster.aigamemaster.benchmark;

import java.util.Objects;

public record GmBenchmarkConfig(String corpusVersion, String model, String modelDigest,
                                 double temperature, int tokenCap, int contextSize, int repetitions) {
    public GmBenchmarkConfig {
        corpusVersion = required(corpusVersion, "corpusVersion");
        model = required(model, "model");
        modelDigest = required(modelDigest, "modelDigest");
        if (!Double.isFinite(temperature) || temperature < 0) throw new IllegalArgumentException("temperature must be finite and non-negative");
        if (tokenCap < 1 || contextSize < 1 || repetitions < 3) throw new IllegalArgumentException("invalid benchmark limits");
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " required");
        return value.trim();
    }
}
