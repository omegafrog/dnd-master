package com.dndmaster.ruleknowledge.infrastructure.ai;

import com.dndmaster.ruleknowledge.application.indexing.ChunkEmbedding;
import com.dndmaster.ruleknowledge.application.indexing.EmbeddingPort;
import com.dndmaster.ruleknowledge.domain.index.RulebookChunk;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.springframework.ai.embedding.EmbeddingModel;

public final class OllamaEmbeddingAdapter implements EmbeddingPort {
    private final EmbeddingModel embeddingModel;

    public OllamaEmbeddingAdapter(EmbeddingModel embeddingModel) {
        this.embeddingModel = Objects.requireNonNull(embeddingModel, "embeddingModel must not be null");
    }

    @Override
    public List<ChunkEmbedding> embed(List<RulebookChunk> chunks, String modelName, int expectedDimension) {
        List<RulebookChunk> immutableChunks = List.copyOf(Objects.requireNonNull(chunks, "chunks must not be null"));
        if (immutableChunks.isEmpty()) {
            throw new IllegalArgumentException("chunks must not be empty");
        }
        if (modelName == null || modelName.isBlank()) {
            throw new IllegalArgumentException("embedding model name must not be blank");
        }
        if (expectedDimension < 1) {
            throw new IllegalArgumentException("expected embedding dimension must be positive");
        }

        final List<float[]> embeddings;
        try {
            embeddings = embeddingModel.embed(immutableChunks.stream().map(RulebookChunk::content).toList());
        } catch (RuntimeException exception) {
            throw new EmbeddingProviderException(exception);
        }
        if (embeddings == null || embeddings.size() != immutableChunks.size()) {
            throw new EmbeddingProviderException(new IllegalStateException("embedding response count does not match chunks"));
        }

        List<ChunkEmbedding> result = new ArrayList<>(immutableChunks.size());
        for (int i = 0; i < immutableChunks.size(); i++) {
            float[] embedding = embeddings.get(i);
            if (embedding == null || embedding.length != expectedDimension) {
                throw new EmbeddingProviderException(new IllegalStateException("embedding dimension does not match index contract"));
            }
            for (float value : embedding) {
                if (!Float.isFinite(value)) {
                    throw new EmbeddingProviderException(new IllegalStateException("embedding values must be finite"));
                }
            }
            result.add(new ChunkEmbedding(immutableChunks.get(i).chunkId(), embedding.clone()));
        }
        return List.copyOf(result);
    }
}
