package com.dndmaster.adventure.application.combat;

public final class CombatMapSpatialConflictException extends RuntimeException {
    private final String code;

    public CombatMapSpatialConflictException(String code) {
        super("combat map spatial action conflicted: " + code);
        this.code = code;
    }

    public String code() { return code; }
}
