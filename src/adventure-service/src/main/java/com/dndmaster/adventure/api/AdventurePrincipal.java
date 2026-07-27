package com.dndmaster.adventure.api;

import java.util.Objects;
import java.util.UUID;

public record AdventurePrincipal(UUID playerId) {
    public AdventurePrincipal {
        Objects.requireNonNull(playerId, "playerId must not be null");
    }
}
