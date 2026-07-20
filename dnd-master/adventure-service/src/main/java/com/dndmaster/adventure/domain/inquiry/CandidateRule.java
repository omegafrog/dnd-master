package com.dndmaster.adventure.domain.inquiry;

import java.util.List;
import java.util.Objects;

public record CandidateRule(String ruleText, List<SourceLocation> sources) {
    public CandidateRule {
        if (ruleText == null || ruleText.isBlank()) throw new IllegalArgumentException("candidate rule must not be blank");
        ruleText = ruleText.trim();
        sources = List.copyOf(Objects.requireNonNull(sources, "sources must not be null"));
        if (sources.isEmpty() || sources.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("a candidate rule must cite at least one source");
        }
    }
}
