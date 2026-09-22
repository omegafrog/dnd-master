package com.dndmaster.character.application.auth;

import java.util.Optional;
import java.util.UUID;

public interface PlayerSessionLookupPort {
    Optional<UUID> resolvePlayerId(String accessToken);
}
