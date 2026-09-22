package com.dndmaster.ruleknowledge.application.registration;

public final class RulebookRegistrationConflictException extends RuntimeException {
    public RulebookRegistrationConflictException(Throwable cause) {
        super("rulebook registration uniqueness conflict", cause);
    }
}
