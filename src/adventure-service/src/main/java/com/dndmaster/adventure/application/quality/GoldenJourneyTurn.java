package com.dndmaster.adventure.application.quality;

import com.dndmaster.adventure.domain.runtime.EffectiveGmProviderSelection;
import com.dndmaster.adventure.domain.runtime.RequestedGmProviderSelection;
import java.util.List;
import java.util.Objects;

/** Immutable evidence for one deterministic or live RAG-026 golden-journey turn. */
public record GoldenJourneyTurn(
        String turnId,
        String playerInput,
        boolean actionReflected,
        boolean neutralFallback,
        int citationExactCount,
        int citationRelevantCount,
        RequestedGmProviderSelection requestedProvider,
        EffectiveGmProviderSelection effectiveProvider,
        EffectiveGmProviderSelection actualProvider,
        long latencyMillis,
        List<String> citationChunkIds,
        String extractionVersion) {
    public GoldenJourneyTurn {
        turnId = required(turnId, "turn id");
        playerInput = required(playerInput, "player input");
        requestedProvider = Objects.requireNonNull(requestedProvider, "requested provider");
        effectiveProvider = Objects.requireNonNull(effectiveProvider, "effective provider");
        actualProvider = Objects.requireNonNull(actualProvider, "actual provider");
        if (citationExactCount < 0 || citationRelevantCount < 0) {
            throw new IllegalArgumentException("citation counts must not be negative");
        }
        if (citationExactCount > citationChunkIds.size() || citationRelevantCount > citationChunkIds.size()) {
            throw new IllegalArgumentException("citation counts cannot exceed citation count");
        }
        if (latencyMillis < 0) throw new IllegalArgumentException("latency must not be negative");
        citationChunkIds = List.copyOf(Objects.requireNonNull(citationChunkIds, "citation chunks"));
        extractionVersion = required(extractionVersion, "extraction version");
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " required");
        return value.trim();
    }
}
