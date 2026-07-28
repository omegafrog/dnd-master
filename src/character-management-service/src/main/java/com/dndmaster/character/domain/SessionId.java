package com.dndmaster.character.domain;

import java.util.Objects;
import java.util.UUID;

public record SessionId(UUID value) {
    public SessionId { Objects.requireNonNull(value, "session id must not be null"); }
    public AdventureId asAdventureId() { return new AdventureId(value); }
}
