package com.dndmaster.adventure.infrastructure.integration;

public final class ResolutionExtractionException extends RuntimeException {
    public ResolutionExtractionException(String message) { super(message); }
    public ResolutionExtractionException(String message, Throwable cause) { super(message, cause); }
}
