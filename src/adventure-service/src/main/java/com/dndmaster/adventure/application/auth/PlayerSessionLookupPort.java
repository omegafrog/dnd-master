package com.dndmaster.adventure.application.auth;

import java.util.Optional;
import java.util.UUID;

public interface PlayerSessionLookupPort {
    Optional<UUID> resolvePlayerId(String accessToken);
}
