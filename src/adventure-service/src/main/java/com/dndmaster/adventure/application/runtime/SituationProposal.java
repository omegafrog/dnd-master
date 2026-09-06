package com.dndmaster.adventure.application.runtime;

import java.util.Objects;

/** The GM's proposed next playable situation and the fact that supports it. */
public record SituationProposal(SituationUpdateProposal update, Basis basis, String reference, boolean required) {
    public enum Basis { SCENARIO, RAG, FALLBACK }

    public SituationProposal {
        update = Objects.requireNonNull(update, "situation update must not be null");
        basis = Objects.requireNonNull(basis, "situation basis must not be null");
        reference = reference == null ? "" : reference.trim();
        if (basis != Basis.FALLBACK && reference.isBlank()) {
            throw new IllegalArgumentException("situation source reference must not be blank");
        }
        if (basis == Basis.FALLBACK && !required) {
            throw new IllegalArgumentException("fallback situation must be required to continue play");
        }
    }
}
