package com.dndmaster.adventure.domain.runtime.fact;

import java.util.Objects;
import java.util.UUID;

public record CommittedWorldFact(UUID factId, String subject, String predicate, String object,
                                 FactVisibility visibility, String provenance, UUID causeTurnId, long version) {
    public CommittedWorldFact {
        Objects.requireNonNull(factId); Objects.requireNonNull(visibility); Objects.requireNonNull(causeTurnId);
        subject = required(subject, "subject"); predicate = required(predicate, "predicate"); object = required(object, "object");
        provenance = required(provenance, "provenance");
        if (version < 1) throw new IllegalArgumentException("fact version must be positive");
    }
    public boolean sameClaimAs(CommittedWorldFact other) { return subject.equals(other.subject) && predicate.equals(other.predicate); }
    private static String required(String value, String name) { if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank"); return value.trim(); }
}
