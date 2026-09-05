package com.dndmaster.adventure.application.runtime;

import com.dndmaster.adventure.domain.runtime.GameState;
import com.dndmaster.adventure.domain.runtime.RuntimeAddedFact;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/** Applies lookup-before-invention and preserves facts already established by play. */
public final class RuntimeFallbackFactPolicy {
    private RuntimeFallbackFactPolicy() {}

    public static Optional<RuntimeAddedFact> create(RuntimeFactLookupResult lookup,
            RuntimeAddedFactCandidate candidate, UUID turnId, GameState gameState,
            List<RuntimeAddedFact> existingFacts) {
        if (lookup == null || candidate == null || turnId == null || gameState == null || existingFacts == null) {
            throw new NullPointerException("fallback inputs must not be null");
        }
        if (lookup.status() != RuntimeFactLookupResult.Status.NOT_FOUND) return Optional.empty();
        String subject = candidate.subject().toLowerCase(Locale.ROOT);
        if (existingFacts.stream().anyMatch(fact -> contains(fact.content(), subject))) return Optional.empty();
        if (gameState.values().entrySet().stream().anyMatch(entry -> contains(entry.getKey(), subject)
                || contains(String.valueOf(entry.getValue()), subject))) return Optional.empty();
        return Optional.of(new RuntimeAddedFact(UUID.randomUUID(), candidate.content(), turnId));
    }

    private static boolean contains(String value, String needle) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(needle);
    }
}
