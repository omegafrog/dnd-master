package com.dndmaster.adventure.infrastructure.integration;

public final class KnowledgeDocumentLookupException extends RuntimeException {
    public KnowledgeDocumentLookupException(String message) {
        super(message);
    }

    public KnowledgeDocumentLookupException(String message, Throwable cause) {
        super(message, cause);
    }
}
