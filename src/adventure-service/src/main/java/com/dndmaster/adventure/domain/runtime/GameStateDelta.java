package com.dndmaster.adventure.domain.runtime;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Uncommitted changes to the Adventure-owned GameState. */
public record GameStateDelta(Map<String, ?> changes) {
    public GameStateDelta {
        Objects.requireNonNull(changes, "game state changes must not be null");
        Map<String, Object> copy = new LinkedHashMap<>();
        changes.forEach((key, value) -> {
            if (key == null || key.isBlank()) throw new IllegalArgumentException("game state change keys must not be blank");
            copy.put(key, value);
        });
        changes = Map.copyOf(copy);
    }

    public static GameStateDelta empty() { return new GameStateDelta(Map.of()); }

    public GameState apply(GameState base) {
        Objects.requireNonNull(base, "base game state must not be null");
        if (changes.isEmpty()) return base;
        Map<String, Object> merged = new LinkedHashMap<>(base.values());
        merged.putAll(changes);
        return new GameState(merged, base.revision() + 1);
    }
}
