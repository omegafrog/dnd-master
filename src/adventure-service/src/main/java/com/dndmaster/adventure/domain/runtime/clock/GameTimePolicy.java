package com.dndmaster.adventure.domain.runtime.clock;

import java.util.OptionalInt;

public final class GameTimePolicy {
    private static final int FALLBACK_SECONDS_PER_TURN = 12;
    private GameTimePolicy() {}
    public static GameDuration durationForTurns(long turns, OptionalInt ruleSecondsPerTurn) {
        if (turns < 0) throw new IllegalArgumentException("turn count must not be negative");
        int seconds = ruleSecondsPerTurn.isPresent() && ruleSecondsPerTurn.getAsInt() > 0
                ? ruleSecondsPerTurn.getAsInt() : FALLBACK_SECONDS_PER_TURN;
        return new GameDuration(turns, Math.multiplyExact(turns, seconds));
    }
    public static int fallbackSecondsPerTurn() { return FALLBACK_SECONDS_PER_TURN; }
}
