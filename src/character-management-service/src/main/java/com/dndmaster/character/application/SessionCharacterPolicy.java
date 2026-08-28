package com.dndmaster.character.application;

public record SessionCharacterPolicy(
        boolean acceptingCharacterSheets,
        boolean nameMutable,
        boolean levelMutable,
        boolean raceMutable,
        boolean characterClassMutable,
        boolean backgroundMutable,
        boolean startingAbilitiesMutable,
        String characterEdition,
        boolean runtimeMutationsAllowed) {
    public SessionCharacterPolicy(boolean acceptingCharacterSheets, boolean nameMutable, boolean levelMutable,
                                  boolean raceMutable, boolean characterClassMutable, boolean backgroundMutable,
                                  boolean startingAbilitiesMutable) {
        this(acceptingCharacterSheets, nameMutable, levelMutable, raceMutable, characterClassMutable,
                backgroundMutable, startingAbilitiesMutable, null, acceptingCharacterSheets);
    }
    public SessionCharacterPolicy(boolean acceptingCharacterSheets, boolean nameMutable, boolean levelMutable) {
        this(acceptingCharacterSheets, nameMutable, levelMutable, true, true, true, true, null, acceptingCharacterSheets);
    }
    public SessionCharacterPolicy(boolean acceptingCharacterSheets, boolean nameMutable, boolean levelMutable,
                                  boolean raceMutable, boolean characterClassMutable, boolean backgroundMutable,
                                  boolean startingAbilitiesMutable, String characterEdition) {
        this(acceptingCharacterSheets, nameMutable, levelMutable, raceMutable, characterClassMutable,
                backgroundMutable, startingAbilitiesMutable, characterEdition, acceptingCharacterSheets);
    }
    public static SessionCharacterPolicy draft() { return new SessionCharacterPolicy(true, true, true, true, true, true, true, null, true); }
    public static SessionCharacterPolicy started(String characterEdition) { return new SessionCharacterPolicy(false, false, false, false, false, false, false, characterEdition, true); }
    public static SessionCharacterPolicy terminated(String characterEdition) { return new SessionCharacterPolicy(false, false, false, false, false, false, false, characterEdition, false); }
}
