package com.dndmaster.adventure.domain.runtime.narrative;

public enum FactAuthority {
    GENERATED_UNEXPOSED(0),
    GENERATED_ESTABLISHED(1),
    CANONICAL_SOURCE(2);

    private final int precedence;

    FactAuthority(int precedence) {
        this.precedence = precedence;
    }

    public boolean outranks(FactAuthority other) {
        return precedence > other.precedence;
    }
}
