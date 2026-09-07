package com.dndmaster.adventure.application.runtime;

import java.util.Set;

/** Only official owning-context ports enter the GM registry. */
public final class OfficialGmToolRegistry {
    private OfficialGmToolRegistry() { }
    public static Set<GmToolDefinition> definitions(OfficialToolPort dice, OfficialToolPort character) {
        return definitions(dice, character, invocation -> GmToolOutcome.rejected("combat map trigger tool is not configured"));
    }
    public static Set<GmToolDefinition> definitions(OfficialToolPort dice, OfficialToolPort character, OfficialToolPort combatMap) {
        String diceSchema = "{\"type\":\"object\",\"required\":[\"scope\",\"count\",\"sides\",\"modifier\"],\"additionalProperties\":false,\"properties\":{\"scope\":{\"type\":\"string\",\"enum\":[\"NPC\",\"ENEMY\",\"SECRET_CHECK\"]},\"count\":{\"type\":\"integer\"},\"sides\":{\"type\":\"integer\"},\"modifier\":{\"type\":\"integer\"}}}";
        String characterSchema = "{\"type\":\"object\",\"required\":[\"characterSheetId\",\"expectedVersion\",\"edition\",\"characterName\",\"level\",\"inspiration\",\"race\",\"characterClass\",\"background\"],\"properties\":{\"characterSheetId\":{\"type\":\"string\"},\"expectedVersion\":{\"type\":\"integer\"},\"level\":{\"type\":\"integer\"},\"inspiration\":{\"type\":\"boolean\"}}}";
        String combatMapSchema = "{\"type\":\"object\",\"required\":[\"mapId\",\"expectedVersion\",\"triggerId\",\"kind\",\"targetIds\",\"qualifyingAction\"],\"additionalProperties\":false,\"properties\":{\"mapId\":{\"type\":\"string\"},\"expectedVersion\":{\"type\":\"integer\"},\"triggerId\":{\"type\":\"string\"},\"kind\":{\"type\":\"string\",\"enum\":[\"COMBAT_ENTRY\",\"ALARM\",\"REINFORCEMENT\",\"BOSS\",\"REWARD\",\"FOG_REVEAL\",\"SUCCESS\",\"FAILURE\",\"EXIT\",\"SURRENDER\"]},\"targetIds\":{\"type\":\"array\"},\"transitionId\":{\"type\":\"string\"},\"qualifyingAction\":{\"type\":\"string\"}}}";
        return Set.of(
                GmToolDefinition.fromOfficial("dice.roll", diceSchema, dice),
                GmToolDefinition.fromOfficial("character.update", characterSchema, character),
                GmToolDefinition.fromOfficial("combat-map.tactical-trigger", combatMapSchema, combatMap));
    }
}
