package com.dndmaster.adventure.application.runtime;

import java.util.Set;

/** Only official owning-context ports enter the GM registry. */
public final class OfficialGmToolRegistry {
    private OfficialGmToolRegistry() { }
    public static Set<GmToolDefinition> definitions(OfficialToolPort dice, OfficialToolPort character) {
        String diceSchema = "{\"type\":\"object\",\"required\":[\"adventureId\",\"ruleSetId\",\"scope\",\"count\",\"sides\",\"modifier\",\"sessionId\",\"turnId\",\"commandId\",\"expectedVersion\"],\"properties\":{\"count\":{\"type\":\"integer\"},\"sides\":{\"type\":\"integer\"},\"modifier\":{\"type\":\"integer\"},\"expectedVersion\":{\"type\":\"integer\"}}}";
        String characterSchema = "{\"type\":\"object\",\"required\":[\"characterSheetId\",\"expectedVersion\",\"edition\",\"characterName\",\"level\",\"inspiration\",\"race\",\"characterClass\",\"background\"],\"properties\":{\"characterSheetId\":{\"type\":\"string\"},\"expectedVersion\":{\"type\":\"integer\"},\"level\":{\"type\":\"integer\"},\"inspiration\":{\"type\":\"boolean\"}}}";
        return Set.of(
                GmToolDefinition.fromOfficial("dice.roll", diceSchema, dice),
                GmToolDefinition.fromOfficial("character.update", characterSchema, character));
    }
}
