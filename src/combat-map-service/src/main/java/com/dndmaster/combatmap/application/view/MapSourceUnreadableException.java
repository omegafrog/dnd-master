package com.dndmaster.combatmap.application.view;

public final class MapSourceUnreadableException extends IllegalArgumentException {
    public MapSourceUnreadableException(String message, Throwable cause) { super(message, cause); }
    public MapSourceUnreadableException(String message) { super(message); }
}
