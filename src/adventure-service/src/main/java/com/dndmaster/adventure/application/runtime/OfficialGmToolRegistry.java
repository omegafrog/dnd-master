package com.dndmaster.adventure.application.runtime;

import java.util.Set;

/** Only official owning-context ports enter the GM registry. */
public final class OfficialGmToolRegistry {
    private OfficialGmToolRegistry() { }
    public static Set<GmToolDefinition> definitions(OfficialToolPort dice, OfficialToolPort character) {
        return Set.of(
                GmToolDefinition.of("dice.roll", "{\"type\":\"object\"}", dice::execute),
                GmToolDefinition.of("character.update", "{\"type\":\"object\"}", character::execute));
    }
}
