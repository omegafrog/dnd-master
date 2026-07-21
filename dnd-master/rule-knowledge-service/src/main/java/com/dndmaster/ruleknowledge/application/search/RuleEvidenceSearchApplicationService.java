package com.dndmaster.ruleknowledge.application.search;

import com.dndmaster.ruleknowledge.application.indexing.EmbeddingPort;
import com.dndmaster.ruleknowledge.domain.index.ChunkId;
import com.dndmaster.ruleknowledge.domain.index.ExtractedContentRange;
import com.dndmaster.ruleknowledge.domain.index.RulebookChunk;
import com.dndmaster.ruleknowledge.domain.rulebook.RulebookId;
import com.dndmaster.ruleknowledge.infrastructure.persistence.PgvectorRuleEvidenceSearchRepository;
import com.dndmaster.ruleknowledge.infrastructure.persistence.RuleSearchHit;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class RuleEvidenceSearchApplicationService {
    private final PgvectorRuleEvidenceSearchRepository searchRepository;
    private final EmbeddingPort embeddingPort;
    private final String embeddingModel;
    private final int embeddingDimension;

    public RuleEvidenceSearchApplicationService(
            PgvectorRuleEvidenceSearchRepository searchRepository,
            EmbeddingPort embeddingPort,
            String embeddingModel,
            int embeddingDimension) {
        this.searchRepository = Objects.requireNonNull(searchRepository, "searchRepository must not be null");
        this.embeddingPort = Objects.requireNonNull(embeddingPort, "embeddingPort must not be null");
        this.embeddingModel = Objects.requireNonNull(embeddingModel, "embeddingModel must not be null");
        if (embeddingDimension <= 0) {
            throw new IllegalArgumentException("embeddingDimension must be positive");
        }
        this.embeddingDimension = embeddingDimension;
    }

    public List<RuleEvidenceResult> search(SearchRuleEvidenceQuery query) {
        Objects.requireNonNull(query, "query must not be null");

        // Embed the query text using the real embedding model
        RulebookChunk queryChunk = new RulebookChunk(
                RulebookId.generate(),
                new ChunkId(UUID.randomUUID()),
                0,
                new ExtractedContentRange(0, query.situation().length()),
                query.situation());

        float[] queryEmbedding = embeddingPort.embed(
                List.of(queryChunk), embeddingModel, embeddingDimension)
                .get(0)
                .vector();

        List<RuleSearchHit> hits = searchRepository.search(
                query.owner(),
                query.selectedRulebooks(),
                queryEmbedding,
                query.limit());

        return hits.stream()
                .map(hit -> new RuleEvidenceResult(
                        hit.rulebookId(),
                        hit.chunkId(),
                        hit.locator(),
                        hit.content(),
                        1.0 - hit.distance()))
                .toList();
    }
}
