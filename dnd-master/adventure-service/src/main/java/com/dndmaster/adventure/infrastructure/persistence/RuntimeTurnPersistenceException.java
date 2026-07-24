package com.dndmaster.adventure.infrastructure.persistence;

public final class RuntimeTurnPersistenceException extends RuntimeException {
    public RuntimeTurnPersistenceException(String message, Throwable cause) {
        super(message, cause);
    }
}
