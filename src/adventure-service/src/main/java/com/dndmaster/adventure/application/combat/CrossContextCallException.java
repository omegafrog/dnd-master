package com.dndmaster.adventure.application.combat;

public final class CrossContextCallException extends RuntimeException {
    public CrossContextCallException(String message) { super(message); }
    public CrossContextCallException(String message, Throwable cause) { super(message, cause); }
}
