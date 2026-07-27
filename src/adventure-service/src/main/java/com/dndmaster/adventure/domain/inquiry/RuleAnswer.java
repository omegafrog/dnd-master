package com.dndmaster.adventure.domain.inquiry;

import java.util.List;
import java.util.Objects;

public record RuleAnswer(String conclusion, List<SourceLocation> sources) {
    public RuleAnswer {
        if (conclusion == null || conclusion.isBlank()) throw new IllegalArgumentException("conclusion must not be blank");
        conclusion = conclusion.trim();
        sources = List.copyOf(Objects.requireNonNull(sources, "sources must not be null"));
        if (sources.isEmpty() || sources.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("a sufficient answer must cite at least one source");
        }
    }
}
