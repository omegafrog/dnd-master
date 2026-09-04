package com.dndmaster.adventure.application.runtime;

import java.util.List;
import java.util.Objects;

/** Immutable adjudication artifact. Narration retries cannot mutate it. */
public record RuntimeTurnResolution(String outcome, Integer diceResult, List<String> outcomes) {
    public RuntimeTurnResolution {
        if (outcome == null || outcome.isBlank()) throw new IllegalArgumentException("resolution outcome must not be blank");
        outcome = outcome.trim();
        if (diceResult != null && diceResult < 1) throw new IllegalArgumentException("dice result must be positive");
        outcomes = List.copyOf(Objects.requireNonNull(outcomes, "resolution outcomes must not be null"));
    }
}
