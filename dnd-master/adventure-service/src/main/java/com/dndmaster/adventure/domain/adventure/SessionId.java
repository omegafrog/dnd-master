package com.dndmaster.adventure.domain.adventure;

import java.util.Objects;
import java.util.UUID;

public record SessionId(UUID value) {
    public SessionId { Objects.requireNonNull(value, "session id must not be null"); }
    public static SessionId generate() { return new SessionId(UUID.randomUUID()); }
}
