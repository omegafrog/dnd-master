package com.dndmaster.diceroll.domain;

public final class RollPermissionDeniedException extends RuntimeException {
    public RollPermissionDeniedException(String message) { super(message); }
}
