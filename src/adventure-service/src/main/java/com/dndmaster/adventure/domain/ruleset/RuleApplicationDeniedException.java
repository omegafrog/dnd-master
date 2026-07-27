package com.dndmaster.adventure.domain.ruleset;

public final class RuleApplicationDeniedException extends RuntimeException {
    public RuleApplicationDeniedException(String message) {
        super(message);
    }
}
