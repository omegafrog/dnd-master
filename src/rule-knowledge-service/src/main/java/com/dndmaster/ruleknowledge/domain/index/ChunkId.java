package com.dndmaster.ruleknowledge.domain.index;

import java.util.Objects;
import java.util.UUID;

public record ChunkId(UUID value) {
    public ChunkId { Objects.requireNonNull(value, "chunk id must not be null"); }
}
