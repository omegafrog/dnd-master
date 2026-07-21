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

    public RuleEvidenceSearchApplicationService(PgvectorRuleEvidenceSearchRepository searchRepository) {
        this.searchRepository = Objects.requireNonNull(searchRepository, "searchRepository must not be null");
    }

    public List<RuleEvidenceResult> search(SearchRuleEvidenceQuery query) {
        Objects.requireNonNull(query, "query must not be null");

        // Create a dummy chunk for embedding the query text
        RulebookChunk dummyChunk = new RulebookChunk(
                RulebookId.generate(),
                new ChunkId(UUID.randomUUID()),
                0,
                new ExtractedContentRange(0, query.situation().length()),
                query.situation());

        // Use a fake embedding for now — the real embedding will come from QueryEmbeddingPort
        float[] queryEmbedding = generateFakeEmbedding(query.situation(), 1536);

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

    private static float[] generateFakeEmbedding(String text, int dimension) {
        float[] embedding = new float[dimension];
        int hash = text.hashCode();
        for (int i = 0; i < dimension; i++) {
            hash = hash * 31 + i;
            embedding[i] = (float) Math.sin(hash) * 0.1f;
        }
        return embedding;
    }
}
