package com.dndmaster.ruleknowledge.application.publication;

import java.util.Objects;

public record EmbeddedPublishedRagChunk(PublishedRagChunk chunk, float[] embedding) {
    public EmbeddedPublishedRagChunk {
        Objects.requireNonNull(chunk, "chunk must not be null");
        Objects.requireNonNull(embedding, "embedding must not be null");
        if (embedding.length == 0) throw new IllegalArgumentException("embedding must not be empty");
        embedding = embedding.clone();
        for (float value : embedding) if (!Float.isFinite(value)) throw new IllegalArgumentException("embedding must be finite");
    }

    @Override public float[] embedding() { return embedding.clone(); }
}
