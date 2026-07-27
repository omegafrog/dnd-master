package com.dndmaster.adventure.domain.adventure;

import java.util.Objects;

/** Party membership plus immutable-after-start initial-attribute policy. */
public record AdventurePartyMember(
        CharacterSheetId characterSheetId,
        ControlMode controlMode,
        boolean nameMutableAfterStart,
        boolean raceMutableAfterStart,
        boolean characterClassMutableAfterStart,
        boolean backgroundMutableAfterStart,
        boolean startingAbilitiesMutableAfterStart,
        boolean levelMutableAfterStart) {
    public AdventurePartyMember {
        Objects.requireNonNull(characterSheetId, "character sheet id must not be null");
        Objects.requireNonNull(controlMode, "control mode must not be null");
    }
}
