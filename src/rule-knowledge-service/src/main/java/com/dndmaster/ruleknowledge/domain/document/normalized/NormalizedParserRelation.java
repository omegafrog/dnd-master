package com.dndmaster.ruleknowledge.domain.document.normalized;

public record NormalizedParserRelation(String childId, String parentId, int level) {
    public NormalizedParserRelation {
        if (childId == null || childId.isBlank() || parentId == null || parentId.isBlank()) {
            throw new IllegalArgumentException("parser relation ids must not be blank");
        }
        if (level < 1) throw new IllegalArgumentException("level must be positive");
    }
}
