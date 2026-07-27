package com.dndmaster.adventure.application.runtime;

import com.dndmaster.adventure.domain.adventure.CharacterSheetId;

/** Reads one character sheet only when its party member's turn is active. */
public interface CharacterSheetReadPort {
    CharacterSheet read(CharacterSheetId characterSheetId);

    record CharacterSheet(CharacterSheetId id, String name, int level) {
        public CharacterSheet {
            if (id == null || name == null || name.isBlank() || level < 1) {
                throw new IllegalArgumentException("valid character sheet snapshot required");
            }
            name = name.trim();
        }
    }
}
