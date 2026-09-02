package com.dndmaster.adventure.domain.adventure;

import java.util.Set;

public record SemanticVerdict(SemanticVerdictType type, double confidence, String claimPath, String summary,
                              Set<String> sourceRefs, Set<String> retrievalRefs, String failureCode) {
    public SemanticVerdict {
        if (type == null) throw new IllegalArgumentException("verdict type is required");
        if (confidence < 0 || confidence > 1) throw new IllegalArgumentException("confidence must be between 0 and 1");
        claimPath = required(claimPath, "claim path");
        summary = required(summary, "summary");
        sourceRefs = sourceRefs == null ? Set.of() : Set.copyOf(sourceRefs);
        retrievalRefs = retrievalRefs == null ? Set.of() : Set.copyOf(retrievalRefs);
        failureCode = failureCode == null ? "" : failureCode.trim();
    }
    public static SemanticVerdict compatible(double confidence, String path, String summary, Set<String> sources, Set<String> retrieval) {
        return new SemanticVerdict(SemanticVerdictType.COMPATIBLE, confidence, path, summary, sources, retrieval, "");
    }
    public static SemanticVerdict contradictory(double confidence, String path, String summary, Set<String> sources, Set<String> retrieval) {
        return new SemanticVerdict(SemanticVerdictType.CONTRADICTORY, confidence, path, summary, sources, retrieval, "");
    }
    public static SemanticVerdict uncertain(String path, String summary) {
        return new SemanticVerdict(SemanticVerdictType.UNCERTAIN, 0, path, summary, Set.of(), Set.of(), "");
    }
    public static SemanticVerdict judgeUnavailable(String summary) {
        return new SemanticVerdict(SemanticVerdictType.UNCERTAIN, 0, "judge", summary, Set.of(), Set.of(), "JUDGE_UNAVAILABLE");
    }
    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
        return value.trim();
    }
}
