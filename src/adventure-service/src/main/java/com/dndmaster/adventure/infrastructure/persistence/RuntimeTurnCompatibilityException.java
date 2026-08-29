package com.dndmaster.adventure.infrastructure.persistence;

/** Stable failure category for runtime rows that cannot be safely projected. */
public final class RuntimeTurnCompatibilityException extends RuntimeException {
    public RuntimeTurnCompatibilityException(String message, Throwable cause) {
        super(message, cause);
    }

    public RuntimeTurnCompatibilityException(String message) {
        super(message);
    }
}
