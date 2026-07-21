package com.dndmaster.ruleknowledge.application.indexing;

import com.dndmaster.ruleknowledge.domain.index.ChunkId;
import java.util.Objects;

public record ChunkEmbedding(ChunkId chunkId, float[] vector) {
    public ChunkEmbedding {
        Objects.requireNonNull(chunkId, "chunkId must not be null");
        Objects.requireNonNull(vector, "vector must not be null");
        if (vector.length == 0) throw new IllegalArgumentException("vector must not be empty");
        vector = vector.clone();
        for (float v : vector) {
            if (!Float.isFinite(v)) throw new IllegalArgumentException("vector values must be finite");
        }
    }

    @Override
    public float[] vector() {
        return vector.clone();
    }
}
