package com.dndmaster.adventure.domain.adventure;

import java.util.Objects;
import java.util.UUID;

public record OwnerPlayerId(UUID value) {
    public OwnerPlayerId { Objects.requireNonNull(value, "owner player id must not be null"); }
}
