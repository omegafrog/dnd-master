package com.dndmaster.aigamemaster.benchmark.rag;

import com.dndmaster.aigamemaster.benchmark.GmBenchmarkCase;
import java.util.List;
import java.util.Objects;

public record RagAbCase(GmBenchmarkCase source, List<String> distractorEvidence) {
    public RagAbCase {
        source = Objects.requireNonNull(source);
        distractorEvidence = List.copyOf(Objects.requireNonNull(distractorEvidence));
        if (distractorEvidence.isEmpty()) throw new IllegalArgumentException("distractor evidence required");
        if (distractorEvidence.stream().anyMatch(source.expectedEvidence()::contains)) throw new IllegalArgumentException("distractor must not contain oracle evidence");
        var oracleTerms = source.expectedEvidence().stream().flatMap(value -> java.util.Arrays.stream(value.toLowerCase().split("[^a-z0-9가-힣]+"))).filter(value -> !value.isBlank()).toList();
        if (distractorEvidence.stream().noneMatch(value -> java.util.Arrays.stream(value.toLowerCase().split("[^a-z0-9가-힣]+"))
                .anyMatch(oracleTerms::contains))) throw new IllegalArgumentException("distractor must be similar to oracle evidence");
    }
    public String id() { return source.id(); }
}
