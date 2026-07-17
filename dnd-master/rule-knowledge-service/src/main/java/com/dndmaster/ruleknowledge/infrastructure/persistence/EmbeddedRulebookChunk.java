package com.dndmaster.ruleknowledge.infrastructure.persistence;

import com.dndmaster.ruleknowledge.domain.index.RulebookChunk;
import java.util.Objects;

public record EmbeddedRulebookChunk(RulebookChunk chunk, String locator, float[] embedding) {
    public EmbeddedRulebookChunk {
        Objects.requireNonNull(chunk, "chunk must not be null");
        if (locator == null || locator.isBlank()) {
            throw new IllegalArgumentException("locator must not be blank");
        }
        locator = locator.trim();
        embedding = validateAndCopy(embedding);
    }

    @Override
    public float[] embedding() {
        return embedding.clone();
    }

    private static float[] validateAndCopy(float[] values) {
        Objects.requireNonNull(values, "embedding must not be null");
        if (values.length == 0) {
            throw new IllegalArgumentException("embedding must not be empty");
        }
        float[] copy = values.clone();
        for (float value : copy) {
            if (!Float.isFinite(value)) {
                throw new IllegalArgumentException("embedding values must be finite");
            }
        }
        return copy;
    }
}
