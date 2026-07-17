package com.dndmaster.character.domain;

import java.util.Objects;
import java.util.UUID;

public record CharacterSheetId(UUID value) {
    public CharacterSheetId { Objects.requireNonNull(value, "character sheet id must not be null"); }
    public static CharacterSheetId generate() { return new CharacterSheetId(UUID.randomUUID()); }
}
