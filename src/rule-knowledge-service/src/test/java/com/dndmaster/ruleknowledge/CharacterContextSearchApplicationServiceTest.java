package com.dndmaster.ruleknowledge;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.dndmaster.ruleknowledge.application.indexing.EmbeddingPort;
import com.dndmaster.ruleknowledge.application.indexing.ChunkEmbedding;
import com.dndmaster.ruleknowledge.application.search.*;
import com.dndmaster.ruleknowledge.domain.index.ChunkId;
import com.dndmaster.ruleknowledge.domain.rulebook.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class CharacterContextSearchApplicationServiceTest {
    private static final OwnerPlayerId OWNER = new OwnerPlayerId(UUID.randomUUID());

    @Test
    void searches_each_type_independently_filters_threshold_deduplicates_and_packs_budget() {
        KnowledgeDocumentId rulebook = KnowledgeDocumentId.generate();
        KnowledgeDocumentId storybook = KnowledgeDocumentId.generate();
        RecordingRepository repository = new RecordingRepository(Map.of(
                DocumentType.RULEBOOK, List.of(
                        hit(rulebook, DocumentType.RULEBOOK, 2, "page 1", "rules", .91),
                        hit(rulebook, DocumentType.RULEBOOK, 2, "page 1", "rules", .90),
                        hit(rulebook, DocumentType.RULEBOOK, 2, "page 2", "low", .20)),
                DocumentType.STORYBOOK, List.of(
                        hit(storybook, DocumentType.STORYBOOK, 4, "page 3", "story", .88))));
        CharacterContextSearchApplicationService service = new CharacterContextSearchApplicationService(
                repository, new FixedEmbeddingPort(), 3);

        List<CharacterContextEvidence> results = service.search(new CharacterContextSearchQuery(
                OWNER,
                Map.of(
                        DocumentType.RULEBOOK, List.of(new CharacterContextDocumentScope(rulebook, 2)),
                        DocumentType.STORYBOOK, List.of(new CharacterContextDocumentScope(storybook, 4)),
                        DocumentType.HANDOUT, List.of()),
                Map.of(DocumentType.RULEBOOK, .5, DocumentType.STORYBOOK, .5, DocumentType.HANDOUT, .5),
                "choose a class", 4));

        assertEquals(List.of("rules", "story"), results.stream().map(CharacterContextEvidence::excerpt).toList());
        assertEquals(List.of(DocumentType.RULEBOOK, DocumentType.STORYBOOK), repository.searchedTypes);
    }

    private static CharacterContextSearchHit hit(
            KnowledgeDocumentId id, DocumentType type, long version, String locator, String excerpt, double score) {
        return new CharacterContextSearchHit(id, type, version, locator, excerpt, score);
    }

    private static final class RecordingRepository implements CharacterContextSearchPort {
        private final Map<DocumentType, List<CharacterContextSearchHit>> hits;
        private final List<DocumentType> searchedTypes = new ArrayList<>();

        private RecordingRepository(Map<DocumentType, List<CharacterContextSearchHit>> hits) { this.hits = hits; }

        @Override
        public List<CharacterContextSearchHit> search(
                OwnerPlayerId owner, DocumentType type, List<CharacterContextDocumentScope> scope,
                float[] embedding) {
            searchedTypes.add(type);
            return hits.getOrDefault(type, List.of());
        }
    }

    private static final class FixedEmbeddingPort implements EmbeddingPort {
        @Override
        public List<ChunkEmbedding> embed(
                List<com.dndmaster.ruleknowledge.domain.index.RulebookChunk> chunks, String model, int dimension) {
            return List.of(new ChunkEmbedding(chunks.getFirst().chunkId(), new float[] {1, 0, 0}));
        }
    }
}
