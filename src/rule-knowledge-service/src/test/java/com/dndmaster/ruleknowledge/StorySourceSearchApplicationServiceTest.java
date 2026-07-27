package com.dndmaster.ruleknowledge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.dndmaster.ruleknowledge.application.indexing.ChunkEmbedding;
import com.dndmaster.ruleknowledge.application.indexing.EmbeddingPort;
import com.dndmaster.ruleknowledge.application.search.StorySourceEvidence;
import com.dndmaster.ruleknowledge.application.search.StorySourceScope;
import com.dndmaster.ruleknowledge.application.search.StorySourceSearchApplicationService;
import com.dndmaster.ruleknowledge.application.search.StorySourceSearchPort;
import com.dndmaster.ruleknowledge.application.search.StorySourceSearchQuery;
import com.dndmaster.ruleknowledge.domain.index.ChunkId;
import com.dndmaster.ruleknowledge.domain.index.ExtractedContentRange;
import com.dndmaster.ruleknowledge.domain.index.RulebookChunk;
import com.dndmaster.ruleknowledge.domain.rulebook.KnowledgeDocumentId;
import com.dndmaster.ruleknowledge.domain.rulebook.OwnerPlayerId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class StorySourceSearchApplicationServiceTest {
    @Test
    void searchesActiveContextThenPerformsOneBoundedPackageFallback() {
        KnowledgeDocumentId documentId = KnowledgeDocumentId.generate();
        RecordingSearchPort searchPort = new RecordingSearchPort(documentId);
        StorySourceSearchApplicationService service = new StorySourceSearchApplicationService(
                searchPort, new FixedEmbeddingPort(), "test", 3);

        List<StorySourceEvidence> results = service.search(new StorySourceSearchQuery(
                new OwnerPlayerId(UUID.randomUUID()),
                List.of(new StorySourceScope(documentId, 7)),
                List.of("page 2 line 1"),
                "find the hidden door",
                2));

        assertEquals(2, results.size());
        assertEquals(List.of(true, false), searchPort.activeContextOnlyCalls);
        assertEquals(documentId, results.getFirst().documentId());
        assertEquals(7, results.getFirst().extractionVersion());
        assertEquals("page 2 line 1", results.getFirst().sourceSpanLocator());
        assertEquals("active door", results.getFirst().excerpt());
        assertEquals("page 3 line 1", results.get(1).sourceSpanLocator());
    }

    @Test
    void rejectsDuplicateDocumentVersionsInsteadOfWideningScope() {
        KnowledgeDocumentId documentId = KnowledgeDocumentId.generate();
        assertThrows(IllegalArgumentException.class, () -> new StorySourceSearchQuery(
                new OwnerPlayerId(UUID.randomUUID()),
                List.of(new StorySourceScope(documentId, 1), new StorySourceScope(documentId, 1)),
                List.of(),
                "question",
                1));
    }

    private static final class RecordingSearchPort implements StorySourceSearchPort {
        private final KnowledgeDocumentId documentId;
        private final java.util.ArrayList<Boolean> activeContextOnlyCalls = new java.util.ArrayList<>();

        private RecordingSearchPort(KnowledgeDocumentId documentId) {
            this.documentId = documentId;
        }

        @Override
        public List<StorySourceEvidence> search(
                StorySourceSearchQuery query, float[] queryEmbedding, boolean activeContextOnly) {
            activeContextOnlyCalls.add(activeContextOnly);
            if (activeContextOnly) {
                return List.of(new StorySourceEvidence(documentId, 7, "page 2 line 1", "active door", 0.8));
            }
            return List.of(
                    new StorySourceEvidence(documentId, 7, "page 2 line 1", "hidden door", 0.9),
                    new StorySourceEvidence(documentId, 7, "page 3 line 1", "fallback door", 0.7));
        }
    }

    private static final class FixedEmbeddingPort implements EmbeddingPort {
        @Override
        public List<ChunkEmbedding> embed(List<RulebookChunk> chunks, String embeddingModel, int expectedDimension) {
            return List.of(new ChunkEmbedding(new ChunkId(UUID.randomUUID()), new float[] {1, 0, 0}));
        }
    }
}
