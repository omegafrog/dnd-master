package com.dndmaster.ruleknowledge.api;

import com.dndmaster.ruleknowledge.domain.rulebook.RulebookId;
import java.util.Objects;
import java.util.UUID;

public record SourceLocationResponse(UUID rulebookId, String locator) {
    public SourceLocationResponse {
        Objects.requireNonNull(rulebookId, "rulebookId must not be null");
        if (locator == null || locator.isBlank()) {
            throw new IllegalArgumentException("locator must not be blank");
        }
    }

    public static SourceLocationResponse from(RulebookId rulebookId, String locator) {
        Objects.requireNonNull(rulebookId, "rulebookId must not be null");
        return new SourceLocationResponse(rulebookId.value(), locator);
    }
}
