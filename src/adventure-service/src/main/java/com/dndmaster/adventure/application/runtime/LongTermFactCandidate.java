package com.dndmaster.adventure.application.runtime;

import java.util.Objects;
import java.util.UUID;

/** A proposed reference to a fact already confirmed by the Adventure Runtime. */
public record LongTermFactCandidate(UUID factId, UUID establishedTurnId, String kind, String relevance,
                                    boolean playerVisible) {
    public LongTermFactCandidate {
        factId = Objects.requireNonNull(factId, "fact id is required");
        establishedTurnId = Objects.requireNonNull(establishedTurnId, "established turn id is required");
        kind = required(kind, "fact kind");
        relevance = required(relevance, "fact relevance");
        if (!(kind.equals("EVENT") || kind.equals("RELATIONSHIP") || kind.equals("GOAL") || kind.equals("THREAT"))) {
            throw new IllegalArgumentException("fact kind must be EVENT, RELATIONSHIP, GOAL, or THREAT");
        }
    }
    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
        return value.trim();
    }
}
