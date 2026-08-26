package com.dndmaster.ruleknowledge.application.publication;

public final class PublicationFailedException extends RuntimeException {
    public PublicationFailedException(Throwable cause) { super("RAG vector publication failed", cause); }
}
