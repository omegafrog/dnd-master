package com.dndmaster.adventure.domain.scenario;

import java.util.Objects;
import java.util.UUID;

public record RequestingPlayerId(UUID value) {
    public RequestingPlayerId {
        Objects.requireNonNull(value, "requesting player id must not be null");
    }
}
