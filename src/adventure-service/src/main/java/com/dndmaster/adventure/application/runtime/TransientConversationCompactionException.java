package com.dndmaster.adventure.application.runtime;

public final class TransientConversationCompactionException extends RuntimeException {
    public TransientConversationCompactionException(String message) { super(message); }
    public TransientConversationCompactionException(String message, Throwable cause) { super(message, cause); }
}
