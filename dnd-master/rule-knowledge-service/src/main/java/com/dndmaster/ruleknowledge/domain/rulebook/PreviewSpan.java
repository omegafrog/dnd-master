package com.dndmaster.ruleknowledge.domain.rulebook;

import java.util.Objects;

public record PreviewSpan(
        String kind,
        int lineNumber,
        int startInclusive,
        int endExclusive,
        String text,
        String locator) {

    public PreviewSpan {
        if (kind == null || kind.isBlank()) {
            throw new IllegalArgumentException("kind must not be blank");
        }
        kind = kind.trim();
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
            locator = kind.toLowerCase() + " " + lineNumber + " chars " + startInclusive + "-" + endExclusive;
        } else {
            locator = locator.trim();
        }
    }
}
