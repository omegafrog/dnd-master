package com.dndmaster.ruleknowledge.infrastructure.ai;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.dndmaster.ruleknowledge.domain.index.ChunkId;
import com.dndmaster.ruleknowledge.domain.index.ExtractedContentRange;
import com.dndmaster.ruleknowledge.domain.index.RulebookChunk;
import com.dndmaster.ruleknowledge.domain.rulebook.RulebookId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;

class OllamaEmbeddingAdapterTest {
    @Test
    void acceptsOnlyFiniteEmbeddingsWithTheRequestedDimension() {
        EmbeddingModel model = mock(EmbeddingModel.class);
        when(model.embed(List.of("first", "second"))).thenReturn(List.of(new float[] {1, 2, 3}, new float[] {4, 5, 6}));

        assertDoesNotThrow(() -> new OllamaEmbeddingAdapter(model).embed(chunks(), "qwen3-embedding:0.6b", 3));
    }

    @Test
    void mapsProviderFailuresAndRejectsDimensionContractViolations() {
        EmbeddingModel failedModel = mock(EmbeddingModel.class);
        when(failedModel.embed(List.of("first", "second"))).thenThrow(new IllegalStateException("provider unavailable"));
        assertThrows(EmbeddingProviderException.class,
                () -> new OllamaEmbeddingAdapter(failedModel).embed(chunks(), "qwen3-embedding:0.6b", 3));

        EmbeddingModel wrongDimensionModel = mock(EmbeddingModel.class);
        when(wrongDimensionModel.embed(List.of("first", "second"))).thenReturn(List.of(new float[] {1, 2}, new float[] {3, 4}));
        assertThrows(EmbeddingProviderException.class,
                () -> new OllamaEmbeddingAdapter(wrongDimensionModel).embed(chunks(), "qwen3-embedding:0.6b", 3));
    }

    private static List<RulebookChunk> chunks() {
        RulebookId rulebookId = RulebookId.generate();
        return List.of(
                new RulebookChunk(rulebookId, new ChunkId(UUID.randomUUID()), 0, new ExtractedContentRange(0, 5), "first", null, null),
                new RulebookChunk(rulebookId, new ChunkId(UUID.randomUUID()), 1, new ExtractedContentRange(5, 11), "second", null, null));
    }
}
