package com.dndmaster.character.infrastructure.persistence;

import com.dndmaster.character.domain.CharacterSheet;
import java.util.Objects;

public record VersionedCharacterSheet(CharacterSheet sheet, long version) {
    public VersionedCharacterSheet {
        Objects.requireNonNull(sheet, "character sheet must not be null");
        if (version < 0) throw new IllegalArgumentException("version must not be negative");
    }
}
