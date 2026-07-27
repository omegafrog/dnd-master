package com.dndmaster.adventure.application.guidance;

import com.dndmaster.adventure.domain.inquiry.SourceLocation;
import java.util.Objects;

public record RuleEvidence(String text, SourceLocation source) {
    public RuleEvidence {
        if (text == null || text.isBlank()) throw new IllegalArgumentException("evidence text must not be blank");
        text = text.trim();
        Objects.requireNonNull(source, "source must not be null");
    }
}
