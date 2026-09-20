package com.dndmaster.adventure.application.runtime;

/** A durable movement follow-up is valid JSON but does not describe its movement result. */
public final class CorruptMovementFollowUpException extends RuntimeException {
    public CorruptMovementFollowUpException(String message) {
        super(message);
    }
}
