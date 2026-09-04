package com.dndmaster.adventure.domain.runtime;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/** The mutable world snapshot owned by an Adventure aggregate. */
public final class GameState {
    private final Map<String, Object> values;
    private final long revision;

    @JsonCreator
    public GameState(@JsonProperty("values") Map<String, ?> values, @JsonProperty("revision") long revision) {
        if (revision < 0) throw new IllegalArgumentException("game state revision must not be negative");
        this.values = copy(values);
        this.revision = revision;
    }

    public static GameState empty() { return new GameState(Map.of(), 0); }

    @JsonProperty("values") public Map<String, Object> values() { return values; }
    @JsonProperty("revision") public long revision() { return revision; }

    private static Map<String, Object> copy(Map<String, ?> input) {
        Objects.requireNonNull(input, "game state values must not be null");
        Map<String, Object> copy = new LinkedHashMap<>();
        input.forEach((key, value) -> {
            if (key == null || key.isBlank()) throw new IllegalArgumentException("game state keys must not be blank");
            copy.put(key, value);
        });
        return Map.copyOf(copy);
    }
}
