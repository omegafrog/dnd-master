package com.dndmaster.ruleknowledge.domain.index;

public record ExtractedContentRange(int startInclusive, int endExclusive) {
    public ExtractedContentRange {
        if (startInclusive < 0 || endExclusive <= startInclusive) {
            throw new IllegalArgumentException("content range must be positive and non-empty");
        }
    }
}
