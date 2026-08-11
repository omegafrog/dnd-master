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
        // An absent storybook constraint must not silently turn a game into solo play.
        // This is an upper bound for the party-size picker, not a mandated party size.
        return new CharacterLimit(6, null, "");
    }

    public Optional<ScenarioSourceReference> source() {
        return Optional.ofNullable(evidence);
    }

    /** True only when the source explicitly mandates this exact party size. */
    public boolean isExactPartySize() {
        String normalized = sourceQuote.toLowerCase(java.util.Locale.ROOT);
        return normalized.matches(".*(?:exactly|must\\s+be|requires?\\s+a\\s+party\\s+of|반드시|정확히|꼭).*");
    }
}
