package com.dndmaster.adventure.infrastructure.integration;

/** Indicates that the read-only scenario lookup provider did not return a usable result. */
public final class ScenarioLookupFailureException extends RuntimeException {
    public ScenarioLookupFailureException(String message, Throwable cause) {
        super(message, cause);
    }
}
