package com.dndmaster.adventure.application.runtime;

/** The current sheet could not be read; the GM request can be retried. */
public final class RuntimeCharacterSheetReadException extends RuntimeException {
    public RuntimeCharacterSheetReadException(String message) {
        super(message);
    }

    public RuntimeCharacterSheetReadException(String message, Throwable cause) {
        super(message, cause);
    }
}
