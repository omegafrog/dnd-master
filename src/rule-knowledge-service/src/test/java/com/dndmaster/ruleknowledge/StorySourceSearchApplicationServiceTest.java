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
import com.dndmaster.ruleknowledge.application.publication.SourceProvenance;
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

    @Test
    void fallsBackToWholePackageWhenNoActiveLocatorsAreProvided() {
        KnowledgeDocumentId documentId = KnowledgeDocumentId.generate();
        RecordingSearchPort searchPort = new RecordingSearchPort(documentId);
        StorySourceSearchApplicationService service = new StorySourceSearchApplicationService(
                searchPort, new FixedEmbeddingPort(), "test", 3);

        List<StorySourceEvidence> results = service.search(new StorySourceSearchQuery(
                new OwnerPlayerId(UUID.randomUUID()),
                List.of(new StorySourceScope(documentId, 7)),
                List.of(),
                "find the hidden door",
                2));

        assertEquals(2, results.size());
        assertEquals(List.of(true, false), searchPort.activeContextOnlyCalls);
        assertEquals("page 3 line 1", results.get(1).sourceSpanLocator());
    }

    @Test
    void openingSearchPrefersEarliestNumberedLocationOverLaterLocationAndSummary() {
        KnowledgeDocumentId documentId = KnowledgeDocumentId.generate();
        StorySourceSearchApplicationService service = new StorySourceSearchApplicationService(
                new OpeningSearchPort(documentId), new FixedEmbeddingPort(), "test", 3);

        List<StorySourceEvidence> results = service.search(new StorySourceSearchQuery(
                new OwnerPlayerId(UUID.randomUUID()),
                List.of(new StorySourceScope(documentId, 7)),
                List.of(),
                "Locate the opening numbered location where the party first enters and can act.",
                3));

        assertEquals(List.of("page 2", "page 3", "page 1"),
                results.stream().map(StorySourceEvidence::sourceSpanLocator).toList());
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

    private static final class OpeningSearchPort implements StorySourceSearchPort {
        private final KnowledgeDocumentId documentId;

        private OpeningSearchPort(KnowledgeDocumentId documentId) {
            this.documentId = documentId;
        }

        @Override
        public List<StorySourceEvidence> search(
                StorySourceSearchQuery query, float[] queryEmbedding, boolean activeContextOnly) {
            if (activeContextOnly) {
                return List.of();
            }
            return List.of(
                    evidence("page 3", 3, "3. Well Room\nA later chamber with a mosaic."),
                    evidence("page 1", 1, "Summary\nThe adventure begins below the brewery."),
                    evidence("page 2", 2, "1. Beer Cellar\nThe party enters through a hatch and can act."));
        }

        private StorySourceEvidence evidence(String locator, int page, String excerpt) {
            return new StorySourceEvidence(documentId, 7, locator, excerpt, 0.9,
                    new SourceProvenance(page, List.of(), List.of(), null, locator));
        }
    }

    private static final class FixedEmbeddingPort implements EmbeddingPort {
        @Override
        public List<ChunkEmbedding> embed(List<RulebookChunk> chunks, String embeddingModel, int expectedDimension) {
            return List.of(new ChunkEmbedding(new ChunkId(UUID.randomUUID()), new float[] {1, 0, 0}));
        }
    }
}
