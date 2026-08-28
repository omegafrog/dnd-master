package com.dndmaster.ruleknowledge.domain.index;

import java.util.Objects;
import java.util.UUID;

public record ChunkId(UUID value) {
    public ChunkId { Objects.requireNonNull(value, "chunk id must not be null"); }

    public static ChunkId fromStableValue(String value) {
        Objects.requireNonNull(value, "chunk id value must not be null");
        if (value.isBlank()) throw new IllegalArgumentException("chunk id value must not be blank");
        return new ChunkId(UUID.nameUUIDFromBytes(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }
}
