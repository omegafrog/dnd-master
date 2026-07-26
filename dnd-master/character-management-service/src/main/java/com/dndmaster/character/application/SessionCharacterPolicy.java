package com.dndmaster.character.application;

public record SessionCharacterPolicy(
        boolean acceptingCharacterSheets,
        boolean nameMutable,
        boolean levelMutable) {
    public static SessionCharacterPolicy draft() { return new SessionCharacterPolicy(true, true, true); }
}
