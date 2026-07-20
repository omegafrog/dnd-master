package com.dndmaster.adventure.domain.adventure;

import java.util.Objects;
import java.util.UUID;

public record AdventureId(UUID value) {
    public AdventureId { Objects.requireNonNull(value, "adventure id must not be null"); }
    public static AdventureId generate() { return new AdventureId(UUID.randomUUID()); }
}
