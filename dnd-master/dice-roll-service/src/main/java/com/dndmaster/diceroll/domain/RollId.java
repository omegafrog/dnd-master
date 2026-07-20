package com.dndmaster.diceroll.domain;

import java.util.Objects;
import java.util.UUID;

public record RollId(UUID value) {
    public RollId { Objects.requireNonNull(value, "roll id must not be null"); }
    public static RollId generate() { return new RollId(UUID.randomUUID()); }
}
