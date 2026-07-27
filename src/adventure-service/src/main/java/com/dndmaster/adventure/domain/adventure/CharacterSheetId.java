package com.dndmaster.adventure.domain.adventure;

import java.util.Objects;
import java.util.UUID;

public record CharacterSheetId(UUID value) {
    public CharacterSheetId { Objects.requireNonNull(value, "character sheet id must not be null"); }
    public CharacterSheetId(String value) { this(UUID.fromString(Objects.requireNonNull(value, "character sheet id must not be null"))); }
}
