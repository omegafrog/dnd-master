package com.dndmaster.adventure.domain.runtime.clock;

import java.util.Objects;

public record GameDuration(long turns, long seconds) {
    public GameDuration {
        if (turns < 0 || seconds < 0) throw new IllegalArgumentException("game duration must not be negative");
    }
    public static GameDuration turns(long turns) { return GameTimePolicy.durationForTurns(turns, java.util.OptionalInt.empty()); }
    public static GameDuration turns(long turns, int secondsPerTurn) {
        return GameTimePolicy.durationForTurns(turns, java.util.OptionalInt.of(secondsPerTurn));
    }
    public static GameDuration seconds(long seconds) { return new GameDuration(0, seconds); }
    public GameDuration plus(GameDuration other) {
        Objects.requireNonNull(other);
        return new GameDuration(turns + other.turns, seconds + other.seconds);
    }
}
