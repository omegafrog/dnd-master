package com.dndmaster.character.domain;

import java.util.Objects;
import java.util.UUID;

public record CharacterSheetUpdate(
        SheetEdition edition, CharacterSheetData data, InputMode inputMode, UUID commandId, long expectedVersion) {
    public CharacterSheetUpdate {
        Objects.requireNonNull(edition, "edition must not be null");
        Objects.requireNonNull(data, "data must not be null");
        Objects.requireNonNull(inputMode, "input mode must not be null");
        Objects.requireNonNull(commandId, "command id must not be null");
        if (expectedVersion < 0) throw new IllegalArgumentException("expected version must not be negative");
    }

    public String fingerprint() {
        return edition + "|" + data + "|" + inputMode;
    }
}
