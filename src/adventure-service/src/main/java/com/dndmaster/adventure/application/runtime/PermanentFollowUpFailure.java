package com.dndmaster.adventure.application.runtime;

/** A durable movement follow-up is invalid and must not be retried. */
public final class PermanentFollowUpFailure extends RuntimeException {
    public PermanentFollowUpFailure(String message) {
        super(message);
    }

    public PermanentFollowUpFailure(String message, Throwable cause) {
        super(message, cause);
    }
}
