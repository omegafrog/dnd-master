package com.dndmaster.aigamemaster.benchmark;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;

public record GmBenchmarkCorpus(String version, List<GmBenchmarkCase> cases) {
    public GmBenchmarkCorpus {
        if (version == null || version.isBlank()) throw new IllegalArgumentException("corpus version required");
        cases = List.copyOf(Objects.requireNonNull(cases));
        if (cases.isEmpty() || cases.size() > 30) throw new IllegalArgumentException("corpus must contain 1..30 cases");
        if (cases.stream().map(GmBenchmarkCase::id).collect(java.util.stream.Collectors.toSet()).size() != cases.size()) {
            throw new IllegalArgumentException("case ids must be unique");
        }
    }

    public String caseIdentity(GmBenchmarkCase benchmarkCase) {
        Objects.requireNonNull(benchmarkCase);
        if (!cases.contains(benchmarkCase)) throw new IllegalArgumentException("case is not in corpus");
        return version + ":" + benchmarkCase.id();
    }
}
