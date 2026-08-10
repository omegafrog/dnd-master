package com.dndmaster.ruleknowledge.domain.rulebook;

import java.util.Objects;

public record SourceSpan(
        int lineNumber,
        int startInclusive,
        int endExclusive,
        String text,
        String locator,
        Integer pageNumber,
        BoundingBox bounds,
        int readingOrder) {
    public SourceSpan(int lineNumber, int startInclusive, int endExclusive, String text, String locator) {
        this(lineNumber, startInclusive, endExclusive, text, locator, null, null, lineNumber - 1);
    }

    public SourceSpan {
        if (lineNumber <= 0) {
            throw new IllegalArgumentException("lineNumber must be positive");
        }
        if (startInclusive < 0) {
            throw new IllegalArgumentException("startInclusive must not be negative");
        }
        if (endExclusive < startInclusive) {
            throw new IllegalArgumentException("endExclusive must not be before startInclusive");
        }
        Objects.requireNonNull(text, "text must not be null");
        if (locator == null || locator.isBlank()) {
            locator = "line " + lineNumber + " chars " + startInclusive + "-" + endExclusive;
        } else {
            locator = locator.trim();
        }
        if (pageNumber != null && pageNumber <= 0) {
            throw new IllegalArgumentException("pageNumber must be positive when present");
        }
        if (readingOrder < 0) {
            throw new IllegalArgumentException("readingOrder must not be negative");
        }
    }
}
