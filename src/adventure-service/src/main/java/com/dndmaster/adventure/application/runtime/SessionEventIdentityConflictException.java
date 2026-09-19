package com.dndmaster.adventure.application.runtime;

/** A durable event id was reused with a different identity and cannot be retried. */
public final class SessionEventIdentityConflictException extends RuntimeException {
    public SessionEventIdentityConflictException(String message) {
        super(message);
    }
}
