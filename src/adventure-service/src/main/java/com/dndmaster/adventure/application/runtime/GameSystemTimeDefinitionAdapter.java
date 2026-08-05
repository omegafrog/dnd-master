package com.dndmaster.adventure.application.runtime;

import java.util.OptionalInt;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Reads the published declarative time field without making the runtime depend on a game system. */
public final class GameSystemTimeDefinitionAdapter {
    private static final Pattern SECONDS_PER_TURN = Pattern.compile("\\\"secondsPerTurn\\\"\\s*:\\s*(\\d+)");
    private GameSystemTimeDefinitionAdapter() {}
    public static OptionalInt secondsPerTurn(String definitionJson) {
        if (definitionJson == null || definitionJson.isBlank()) return OptionalInt.empty();
        Matcher matcher = SECONDS_PER_TURN.matcher(definitionJson);
        if (!matcher.find()) return OptionalInt.empty();
        int seconds = Integer.parseInt(matcher.group(1));
        return seconds > 0 ? OptionalInt.of(seconds) : OptionalInt.empty();
    }
}
