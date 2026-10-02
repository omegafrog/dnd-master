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
        boolean runtimeMutationsAllowed,
        boolean sessionActive) {
    public SessionCharacterPolicy(boolean acceptingCharacterSheets, boolean nameMutable, boolean levelMutable,
                                  boolean raceMutable, boolean characterClassMutable, boolean backgroundMutable,
                                  boolean startingAbilitiesMutable, String characterEdition,
                                  boolean runtimeMutationsAllowed) {
        this(acceptingCharacterSheets, nameMutable, levelMutable, raceMutable, characterClassMutable,
                backgroundMutable, startingAbilitiesMutable, characterEdition, runtimeMutationsAllowed,
                acceptingCharacterSheets || runtimeMutationsAllowed);
    }
    public SessionCharacterPolicy(boolean acceptingCharacterSheets, boolean nameMutable, boolean levelMutable,
                                  boolean raceMutable, boolean characterClassMutable, boolean backgroundMutable,
                                  boolean startingAbilitiesMutable) {
        this(acceptingCharacterSheets, nameMutable, levelMutable, raceMutable, characterClassMutable,
                backgroundMutable, startingAbilitiesMutable, null, acceptingCharacterSheets, acceptingCharacterSheets);
    }
    public SessionCharacterPolicy(boolean acceptingCharacterSheets, boolean nameMutable, boolean levelMutable) {
        this(acceptingCharacterSheets, nameMutable, levelMutable, true, true, true, true, null,
                acceptingCharacterSheets, acceptingCharacterSheets);
    }
    public SessionCharacterPolicy(boolean acceptingCharacterSheets, boolean nameMutable, boolean levelMutable,
                                  boolean raceMutable, boolean characterClassMutable, boolean backgroundMutable,
                                  boolean startingAbilitiesMutable, String characterEdition) {
        this(acceptingCharacterSheets, nameMutable, levelMutable, raceMutable, characterClassMutable,
                backgroundMutable, startingAbilitiesMutable, characterEdition, acceptingCharacterSheets,
                acceptingCharacterSheets);
    }
    public static SessionCharacterPolicy draft() { return new SessionCharacterPolicy(true, true, true, true, true, true, true, null, true, true); }
    public static SessionCharacterPolicy started(String characterEdition) { return new SessionCharacterPolicy(false, false, false, false, false, false, false, characterEdition, true, true); }
    public static SessionCharacterPolicy terminated(String characterEdition) { return new SessionCharacterPolicy(false, false, false, false, false, false, false, characterEdition, false, false); }
}
