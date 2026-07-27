package com.dndmaster.character.application;

public record SessionCharacterPolicy(
        boolean acceptingCharacterSheets,
        boolean nameMutable,
        boolean levelMutable,
        boolean raceMutable,
        boolean characterClassMutable,
        boolean backgroundMutable,
        boolean startingAbilitiesMutable) {
    public SessionCharacterPolicy(boolean acceptingCharacterSheets, boolean nameMutable, boolean levelMutable) {
        this(acceptingCharacterSheets, nameMutable, levelMutable, true, true, true, true);
    }
    public static SessionCharacterPolicy draft() { return new SessionCharacterPolicy(true, true, true, true, true, true, true); }
}
