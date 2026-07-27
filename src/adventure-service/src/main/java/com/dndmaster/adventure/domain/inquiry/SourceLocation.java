package com.dndmaster.adventure.domain.inquiry;

import java.util.Objects;

public record SourceLocation(RulebookId rulebookId, String locator) {
    public SourceLocation {
        Objects.requireNonNull(rulebookId, "rulebook id must not be null");
        if (locator == null || locator.isBlank()) throw new IllegalArgumentException("source locator must not be blank");
        locator = locator.trim();
    }
}
