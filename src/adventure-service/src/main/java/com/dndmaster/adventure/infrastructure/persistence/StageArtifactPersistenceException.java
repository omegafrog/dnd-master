package com.dndmaster.adventure.infrastructure.persistence;

public final class StageArtifactPersistenceException extends RuntimeException {
    public StageArtifactPersistenceException(String message, Throwable cause) { super(message, cause); }
    public StageArtifactPersistenceException(String message) { super(message); }
}
