package com.dndmaster.ruleknowledge.domain.index;

import java.util.Objects;
import java.util.UUID;

public record IndexId(UUID value) {
    public IndexId { Objects.requireNonNull(value, "index id must not be null"); }

    public static IndexId generate() { return new IndexId(UUID.randomUUID()); }
}
