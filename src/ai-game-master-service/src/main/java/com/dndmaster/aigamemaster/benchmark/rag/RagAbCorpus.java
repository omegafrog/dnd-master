package com.dndmaster.aigamemaster.benchmark.rag;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;

public record RagAbCorpus(String version, List<RagAbCase> cases) {
    public RagAbCorpus {
        if (version == null || version.isBlank()) throw new IllegalArgumentException("corpus version required");
        cases = List.copyOf(Objects.requireNonNull(cases));
        if (cases.isEmpty() || new HashSet<>(cases.stream().map(RagAbCase::id).toList()).size() != cases.size()) {
            throw new IllegalArgumentException("RAG A/B cases must be non-empty and unique");
        }
    }
}
