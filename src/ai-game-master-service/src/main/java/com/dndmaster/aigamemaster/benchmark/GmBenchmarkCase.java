package com.dndmaster.aigamemaster.benchmark;

import java.util.List;
import java.util.Objects;

public record GmBenchmarkCase(String id, String prompt, List<String> expectedEvidence, List<String> protectedFacts) {
    public GmBenchmarkCase {
        id = required(id, "id");
        prompt = required(prompt, "prompt");
        expectedEvidence = List.copyOf(Objects.requireNonNull(expectedEvidence));
        protectedFacts = List.copyOf(Objects.requireNonNull(protectedFacts));
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " required");
        return value.trim();
    }
}
