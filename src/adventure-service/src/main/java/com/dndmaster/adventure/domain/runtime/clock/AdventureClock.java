package com.dndmaster.adventure.domain.runtime.clock;

import java.util.Objects;
import java.util.UUID;

public final class AdventureClock {
    private final UUID sessionId;
    private final long version;
    private final long turnsElapsed;
    private final long secondsElapsed;
    private final UUID lastCauseTurnId;

    private AdventureClock(UUID sessionId, long version, long turnsElapsed, long secondsElapsed, UUID lastCauseTurnId) {
        this.sessionId = Objects.requireNonNull(sessionId);
        this.version = version;
        this.turnsElapsed = turnsElapsed;
        this.secondsElapsed = secondsElapsed;
        this.lastCauseTurnId = lastCauseTurnId;
    }
    public static AdventureClock initial(UUID sessionId) { return new AdventureClock(sessionId, 0, 0, 0, null); }
    public static AdventureClock rehydrate(UUID sessionId, long version, long turns, long seconds, UUID causeTurnId) {
        if (version < 0 || turns < 0 || seconds < 0) throw new IllegalArgumentException("invalid adventure clock");
        return new AdventureClock(sessionId, version, turns, seconds, causeTurnId);
    }
    public AdventureClock advance(GameDuration duration, UUID causeTurnId) {
        Objects.requireNonNull(duration); Objects.requireNonNull(causeTurnId);
        if (causeTurnId.equals(lastCauseTurnId)) return this;
        if (duration.turns() == 0 && duration.seconds() == 0) return this;
        return new AdventureClock(sessionId, version + 1, turnsElapsed + duration.turns(), secondsElapsed + duration.seconds(), causeTurnId);
    }
    public UUID sessionId() { return sessionId; }
    public long version() { return version; }
    public long turnsElapsed() { return turnsElapsed; }
    public long secondsElapsed() { return secondsElapsed; }
    public UUID lastCauseTurnId() { return lastCauseTurnId; }
}
