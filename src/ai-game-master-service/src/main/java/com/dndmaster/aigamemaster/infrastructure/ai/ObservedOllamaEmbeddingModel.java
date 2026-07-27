package com.dndmaster.aigamemaster.infrastructure.ai;

import java.util.Objects;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;

public final class ObservedOllamaEmbeddingModel implements EmbeddingModel {
    private final EmbeddingModel delegate;
    private final OllamaCallObservability observability;

    ObservedOllamaEmbeddingModel(EmbeddingModel delegate, OllamaCallObservability observability) {
        this.delegate = Objects.requireNonNull(delegate);
        this.observability = Objects.requireNonNull(observability);
    }

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        return observability.invoke(() -> delegate.call(request));
    }

    @Override
    public float[] embed(Document document) {
        return observability.invoke(() -> delegate.embed(document));
    }

    @Override
    public int dimensions() {
        return delegate.dimensions();
    }

    public EmbeddingModel delegate() {
        return delegate;
    }
}
