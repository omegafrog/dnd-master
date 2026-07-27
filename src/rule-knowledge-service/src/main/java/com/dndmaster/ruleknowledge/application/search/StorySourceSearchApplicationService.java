package com.dndmaster.ruleknowledge.application.search;

import com.dndmaster.ruleknowledge.application.indexing.EmbeddingPort;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;

public final class StorySourceSearchApplicationService {
    private final StorySourceSearchPort searchPort;
    private final EmbeddingPort embeddingPort;
    private final String embeddingModel;
    private final int embeddingDimension;

    public StorySourceSearchApplicationService(
            StorySourceSearchPort searchPort,
            EmbeddingPort embeddingPort,
            String embeddingModel,
            int embeddingDimension) {
        this.searchPort = Objects.requireNonNull(searchPort, "search port must not be null");
        this.embeddingPort = Objects.requireNonNull(embeddingPort, "embedding port must not be null");
        this.embeddingModel = Objects.requireNonNull(embeddingModel, "embedding model must not be null");
        if (embeddingDimension <= 0) {
            throw new IllegalArgumentException("embedding dimension must be positive");
        }
        this.embeddingDimension = embeddingDimension;
    }

    public List<StorySourceEvidence> search(StorySourceSearchQuery query) {
        Objects.requireNonNull(query, "query must not be null");
        float[] embedding = embeddingPort.embed(
                        List.of(new com.dndmaster.ruleknowledge.domain.index.RulebookChunk(
                                query.packageScope().getFirst().documentId().asRulebookId(),
                                new com.dndmaster.ruleknowledge.domain.index.ChunkId(java.util.UUID.randomUUID()),
                                0,
                                new com.dndmaster.ruleknowledge.domain.index.ExtractedContentRange(0, query.situation().length()),
                                query.situation(),
                                null,
                                null)),
                        embeddingModel,
                        embeddingDimension)
                .getFirst()
                .vector();

        List<StorySourceEvidence> active = searchPort.search(query, embedding, true);
        if (active.size() >= query.limit() || query.activeLocators().isEmpty()) {
            return active.stream().limit(query.limit()).toList();
        }
        List<StorySourceEvidence> fallback = searchPort.search(query, embedding, false);
        var merged = new LinkedHashMap<String, StorySourceEvidence>();
        active.forEach(result -> merged.put(evidenceKey(result), result));
        fallback.forEach(result -> merged.putIfAbsent(evidenceKey(result), result));
        return merged.values().stream().limit(query.limit()).toList();
    }

    private static String evidenceKey(StorySourceEvidence evidence) {
        return evidence.documentId().value() + ":" + evidence.extractionVersion() + ":" + evidence.sourceSpanLocator();
    }
}
