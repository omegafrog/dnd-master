package com.dndmaster.ruleknowledge.application.search;

import com.dndmaster.ruleknowledge.application.indexing.EmbeddingPort;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.Set;

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
        if (query.activeLocators().isEmpty()) {
            return hybridize(query, searchPort.search(query, embedding, false));
        }
        if (active.size() >= query.limit()) {
            return hybridize(query, active);
        }
        List<StorySourceEvidence> fallback = searchPort.search(query, embedding, false);
        var merged = new LinkedHashMap<String, StorySourceEvidence>();
        active.forEach(result -> merged.put(evidenceKey(result), result));
        fallback.forEach(result -> merged.putIfAbsent(evidenceKey(result), result));
        return hybridize(query, List.copyOf(merged.values()));
    }

    private List<StorySourceEvidence> hybridize(StorySourceSearchQuery query, List<StorySourceEvidence> evidence) {
        RetrievalScope.Builder builder = RetrievalScope.builder(query.owner().value())
                .sessionId("legacy-story-search").packageId("legacy-story-search").stage("current");
        for (StorySourceScope item : query.packageScope()) {
            builder.document(item.documentId(), com.dndmaster.ruleknowledge.domain.rulebook.DocumentType.STORYBOOK,
                    item.extractionVersion());
        }
        for (String visibility : Set.of("PLAYER_VISIBLE", "GM_ONLY", "NPC_PRIVATE", "REVEALED_AFTER_EVENT", "DISCOVERED", "PUBLIC_SUMMARY")) {
            builder.allowedVisibility(visibility);
        }
        RetrievalScope scope = builder.build();
        HybridRetrievalService retrieval = new HybridRetrievalService(
                (ignored, ignoredScope, limit) -> candidates(query, evidence, false, limit),
                (ignored, ignoredScope, limit) -> candidates(query, evidence, true, limit));
        return retrieval.search(query.situation(), scope, query.limit()).stream()
                .map(candidate -> evidence.stream().filter(item -> candidate.locator().equals(item.sourceSpanLocator())).findFirst().orElseThrow())
                .toList();
    }

    private static List<HybridRetrievalCandidate> candidates(StorySourceSearchQuery query,
            List<StorySourceEvidence> evidence, boolean keyword, int limit) {
        return evidence.stream().limit(limit).map(item -> new HybridRetrievalCandidate(query.owner().value(), item.documentId(),
                com.dndmaster.ruleknowledge.domain.rulebook.DocumentType.STORYBOOK, item.extractionVersion(), item.sourceSpanLocator(),
                item.excerpt(), keyword ? 0d : item.score(), keyword ? lexicalScore(query.situation(), item.excerpt()) : 0d,
                UUID.nameUUIDFromBytes((item.documentId().value() + ":" + item.sourceSpanLocator()).getBytes()),
                "legacy-story-search", "legacy-story-search", "current", item.visibility())).toList();
    }

    private static double lexicalScore(String query, String excerpt) {
        Set<String> terms = new java.util.HashSet<>(java.util.Arrays.asList(query.toLowerCase().split("\\W+")));
        return terms.isEmpty() ? 0d : (double) terms.stream().filter(term -> !term.isBlank() && excerpt.toLowerCase().contains(term)).count() / terms.size();
    }

    private static String evidenceKey(StorySourceEvidence evidence) {
        return evidence.documentId().value() + ":" + evidence.extractionVersion() + ":" + evidence.sourceSpanLocator();
    }
}
