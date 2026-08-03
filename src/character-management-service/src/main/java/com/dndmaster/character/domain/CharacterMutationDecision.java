package com.dndmaster.character.domain;

import java.util.List;
import java.util.Objects;

public record CharacterMutationDecision(boolean accepted, List<RuleViolation> violations) {
    public CharacterMutationDecision {
        violations = List.copyOf(Objects.requireNonNull(violations, "violations must not be null"));
        if (accepted && !violations.isEmpty()) {
            throw new IllegalArgumentException("accepted mutation must not contain violations");
        }
        if (!accepted && violations.isEmpty()) {
            throw new IllegalArgumentException("rejected mutation must contain at least one violation");
        }
    }

    public static CharacterMutationDecision accept() {
        return new CharacterMutationDecision(true, List.of());
    }

    public static CharacterMutationDecision reject(List<RuleViolation> violations) {
        return new CharacterMutationDecision(false, violations);
    }
}
