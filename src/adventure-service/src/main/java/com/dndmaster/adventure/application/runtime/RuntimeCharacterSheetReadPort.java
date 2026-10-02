package com.dndmaster.adventure.application.runtime;

import java.util.UUID;

/** Reads the current character sheet from Character Management for a GM request. */
@FunctionalInterface
public interface RuntimeCharacterSheetReadPort {
    String read(UUID characterSheetId);
}
