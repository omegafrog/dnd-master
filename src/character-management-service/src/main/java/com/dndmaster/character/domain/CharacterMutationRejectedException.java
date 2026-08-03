package com.dndmaster.character.domain;

import java.util.List;

public final class CharacterMutationRejectedException extends RuntimeException {
    private final List<RuleViolation> violations;

    public CharacterMutationRejectedException(List<RuleViolation> violations) {
        super(violations == null || violations.isEmpty()
                ? "character mutation rejected"
                : violations.stream().map(RuleViolation::code).reduce((left, right) -> left + "," + right).orElse("character mutation rejected"));
        this.violations = violations == null ? List.of() : List.copyOf(violations);
    }

    public List<RuleViolation> violations() {
        return violations;
    }
}
