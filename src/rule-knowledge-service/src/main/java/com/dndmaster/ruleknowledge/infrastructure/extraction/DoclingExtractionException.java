package com.dndmaster.ruleknowledge.infrastructure.extraction;

public final class DoclingExtractionException extends RuntimeException {
    private final boolean retryable;

    public DoclingExtractionException(String message, boolean retryable) { super(message); this.retryable = retryable; }
    public DoclingExtractionException(String message, Throwable cause, boolean retryable) { super(message, cause); this.retryable = retryable; }
    public boolean retryable() { return retryable; }
}
