package com.dndmaster.ruleknowledge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dndmaster.ruleknowledge.application.indexing.ChunkEmbedding;
import com.dndmaster.ruleknowledge.application.indexing.EmbeddingPort;
import com.dndmaster.ruleknowledge.domain.index.ChunkId;
import com.dndmaster.ruleknowledge.domain.index.ExtractedContentRange;
import com.dndmaster.ruleknowledge.domain.index.RulebookChunk;
import com.dndmaster.ruleknowledge.domain.rulebook.RulebookId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ChunkEmbeddingTest {

    @Test
    void defensiveCopyOnConstruction() {
        float[] vector = {1f, 2f, 3f};
        ChunkId chunkId = new ChunkId(UUID.randomUUID());

        ChunkEmbedding ce = new ChunkEmbedding(chunkId, vector);
        vector[0] = 999f;

        assertEquals(1f, ce.vector()[0]);
    }

    @Test
    void defensiveCopyOnAccessor() {
        ChunkEmbedding ce = new ChunkEmbedding(new ChunkId(UUID.randomUUID()), new float[]{1f, 2f});

        float[] first = ce.vector();
        float[] second = ce.vector();
        first[0] = 999f;

        assertEquals(1f, second[0]);
    }

    @Test
    void rejectsEmptyVector() {
        assertThrows(IllegalArgumentException.class,
                () -> new ChunkEmbedding(new ChunkId(UUID.randomUUID()), new float[]{}));
    }

    @Test
    void rejectsNaN() {
        assertThrows(IllegalArgumentException.class,
                () -> new ChunkEmbedding(new ChunkId(UUID.randomUUID()), new float[]{Float.NaN}));
    }

    @Test
    void rejectsInfinite() {
        assertThrows(IllegalArgumentException.class,
                () -> new ChunkEmbedding(new ChunkId(UUID.randomUUID()), new float[]{Float.POSITIVE_INFINITY}));
    }

    private static void assertThrows(Class<? extends Throwable> type, Runnable runnable) {
        try {
            runnable.run();
            throw new AssertionError("Expected " + type.getSimpleName() + " but none thrown");
        } catch (Throwable t) {
            if (!type.isInstance(t)) {
                throw new AssertionError("Expected " + type.getSimpleName() + " but got " + t.getClass().getSimpleName());
            }
        }
    }
}
