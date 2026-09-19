package com.dndmaster.adventure.application.runtime;

/** A persisted typed continuation outcome is corrupt and cannot be retried safely. */
public final class CorruptRuntimeContinuationOutcomeException extends RuntimeException {
    public CorruptRuntimeContinuationOutcomeException(String message, Throwable cause) {
        super(message, cause);
    }
}
