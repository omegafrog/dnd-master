package com.dndmaster.ruleknowledge.application.auth;

import java.util.Optional;
import java.util.UUID;

public interface PlayerSessionLookupPort {
    Optional<UUID> resolvePlayerId(String accessToken);
}
