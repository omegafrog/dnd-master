package com.dndmaster.character.application;

public record SessionCharacterPolicy(
        boolean acceptingCharacterSheets,
        boolean nameMutable,
        boolean levelMutable,
        boolean raceMutable,
        boolean characterClassMutable,
        boolean backgroundMutable,
        boolean startingAbilitiesMutable,
        String characterEdition) {
    public SessionCharacterPolicy(boolean acceptingCharacterSheets, boolean nameMutable, boolean levelMutable,
                                  boolean raceMutable, boolean characterClassMutable, boolean backgroundMutable,
                                  boolean startingAbilitiesMutable) {
        this(acceptingCharacterSheets, nameMutable, levelMutable, raceMutable, characterClassMutable,
                backgroundMutable, startingAbilitiesMutable, null);
    }
    public SessionCharacterPolicy(boolean acceptingCharacterSheets, boolean nameMutable, boolean levelMutable) {
        this(acceptingCharacterSheets, nameMutable, levelMutable, true, true, true, true, null);
    }
    public static SessionCharacterPolicy draft() { return new SessionCharacterPolicy(true, true, true, true, true, true, true, null); }
}
