package com.dndmaster.adventure.application.runtime;

import java.util.OptionalInt;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Reads the published declarative time field without making the runtime depend on a game system. */
public final class GameSystemTimeDefinitionAdapter {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private GameSystemTimeDefinitionAdapter() {}
    public static OptionalInt secondsPerTurn(String definitionJson) {
        if (definitionJson == null || definitionJson.isBlank()) return OptionalInt.empty();
        try {
            JsonNode time = MAPPER.readTree(definitionJson).path("time");
            int seconds = time.path("secondsPerTurn").asInt(0);
            return seconds > 0 ? OptionalInt.of(seconds) : OptionalInt.empty();
        } catch (Exception ignored) {
            return OptionalInt.empty();
        }
    }
}
