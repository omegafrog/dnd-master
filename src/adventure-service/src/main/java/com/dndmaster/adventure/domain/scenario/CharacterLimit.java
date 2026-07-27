package com.dndmaster.adventure.domain.scenario;

import java.util.Objects;
import java.util.Optional;

/** Immutable character capacity compiled from STORYBOOK source text. */
public record CharacterLimit(int maximumCharacters, ScenarioSourceReference evidence, String sourceQuote) {
    public CharacterLimit {
        if (maximumCharacters < 1) throw new IllegalArgumentException("maximum characters must be positive");
        sourceQuote = sourceQuote == null ? "" : sourceQuote;
        if (evidence == null && !sourceQuote.isEmpty()) {
            throw new IllegalArgumentException("character limit source quote requires a source");
        }
        if (evidence != null) Objects.requireNonNull(evidence.locator(), "character limit source locator must not be null");
    }

    public static CharacterLimit defaultLimit() {
        return new CharacterLimit(1, null, "");
    }

    public Optional<ScenarioSourceReference> source() {
        return Optional.ofNullable(evidence);
    }
}
