package com.dndmaster.ruleknowledge.application.publication;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.dndmaster.ruleknowledge.application.indexing.ChunkEmbedding;
import com.dndmaster.ruleknowledge.application.indexing.EmbeddingPort;
import com.dndmaster.ruleknowledge.domain.index.ExtractedContentRange;
import com.dndmaster.ruleknowledge.domain.index.RulebookChunk;
import com.dndmaster.ruleknowledge.domain.rulebook.OwnerPlayerId;
import com.dndmaster.ruleknowledge.domain.rulebook.RulebookId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RagExtractionPublicationServiceTest {
    @Test
    void publishesSearchablePagesAndKeepsAnUnreadablePageInTheManifest() {
        RecordingRepository repository = new RecordingRepository();
        RagExtractionPublicationService service = service(repository,
                (chunks, model, dimension) -> embeddings(chunks));

        RagExtractionVersion published = service.publish(request(
                List.of(new RagExtractionPage(1, "VALIDATED", 1, List.of()),
                        new RagExtractionPage(2, "NEEDS_REVIEW", 1, List.of("TEXT_UNUSABLE")))));

        assertEquals(ExtractionPublicationStatus.INDEXED, published.status());
        assertEquals(1, repository.publishCalls);
        assertEquals(0, repository.failedCalls);
        assertEquals(2, repository.publishedRequest.pages().size());
        assertEquals("NEEDS_REVIEW", repository.publishedRequest.pages().get(1).status());
    }

    @Test
    void rejectsSearchChunkSourcedFromUnreadablePageInPartialPublication() {
        RecordingRepository repository = new RecordingRepository();
        RagExtractionPublicationService service = service(repository, (chunks, model, dimension) -> {
            throw new AssertionError("embedding must not receive a chunk from an unreadable page");
        });
        PublishedRagChunk unreadableChunk = new PublishedRagChunk("processor-chunk-1", 0,
                "Unreadable page text", "Unreadable page text",
                new SourceProvenance(2, List.of(), List.of(), null, "page=2"));

        assertThrows(PublicationBlockedException.class, () -> service.publish(request(
                List.of(new RagExtractionPage(1, "VALIDATED", 1, List.of()),
                        new RagExtractionPage(2, "NEEDS_REVIEW", 1, List.of("TEXT_UNUSABLE"))),
                List.of(unreadableChunk))));

        assertEquals(0, repository.publishCalls);
        assertEquals(1, repository.failedCalls);
        assertEquals(ExtractionPublicationStatus.NEEDS_REVIEW, repository.failedStatus);
    }

    @Test
    void acceptsTextFromStructurallyUncertainPageWhenItsSourceIsMarkedUsable() {
        RecordingRepository repository = new RecordingRepository();
        RagExtractionPublicationService service = service(repository,
                (chunks, model, dimension) -> embeddings(chunks));

        RagExtractionVersion published = service.publish(request(List.of(
                new RagExtractionPage(1, "USABLE_WITH_WARNINGS", 1, List.of("STRUCTURE_UNCERTAIN")))));

        assertEquals(ExtractionPublicationStatus.INDEXED, published.status());
        assertEquals(1, repository.publishCalls);
    }

    @Test
    void publishesValidatedPagesAndExplicitlyExcludedForms() {
        RecordingRepository repository = new RecordingRepository();
        RagExtractionPublicationService service = service(repository, (chunks, model, dimension) -> embeddings(chunks));

        RagExtractionVersion published = service.publish(request(List.of(
                new RagExtractionPage(1, "VALIDATED", 1, List.of()),
                new RagExtractionPage(2, "EXCLUDED_FORM", 1, List.of(), "form", formEvidence()))));

        assertEquals(ExtractionPublicationStatus.INDEXED, published.status());
        assertEquals(1, repository.publishCalls);
    }

    @Test
    void rejectsChunksWithAnySourceSpanOnAnExcludedForm() {
        RecordingRepository repository = new RecordingRepository();
        RagExtractionPublicationService service = service(repository, (chunks, model, dimension) -> {
            throw new AssertionError("embedding must not be called for a chunk sourced from an excluded form");
        });
        PublishedRagChunk formChunk = new PublishedRagChunk("processor-chunk-1", 0, "Form text", "Form text",
                new SourceProvenance(1, List.of(), List.of(), null, "page=1", List.of(
                        new SourceProvenance.SourceSpan(1, null, null, null, null, null, List.of(), null, null))));

        assertThrows(PublicationBlockedException.class, () -> service.publish(request(List.of(
                new RagExtractionPage(1, "EXCLUDED_FORM", 1, List.of(), "form", formEvidence()),
                new RagExtractionPage(2, "VALIDATED", 1, List.of())), List.of(formChunk))));
        assertEquals(0, repository.publishCalls);
    }

    @Test
    void oversizedChunkIsRejectedBeforeEmbeddingOrPublication() {
        RecordingRepository repository = new RecordingRepository();
        RagExtractionPublicationService service = service(repository, (chunks, model, dimension) -> {
            throw new AssertionError("embedding must not be called for an oversized chunk");
        });
        PublishedRagChunk oversized = new PublishedRagChunk(
                "processor-chunk-1", 0, "x".repeat(6001), "x".repeat(6001),
                new SourceProvenance(1, List.of("Chapter 1"), List.of(), null, "page=1"));

        assertThrows(PublicationBlockedException.class, () -> service.publish(request(
                List.of(new RagExtractionPage(1, "VALIDATED", 1, List.of())), List.of(oversized))));

        assertEquals(0, repository.publishCalls);
        assertEquals(1, repository.failedCalls);
        assertEquals(ExtractionPublicationStatus.NEEDS_REVIEW, repository.failedStatus);
    }

    @Test
    void publicationCountsSupplementaryCharactersLikePythonCharacterLength() {
        RecordingRepository repository = new RecordingRepository();
        RagExtractionPublicationService service = service(repository, (chunks, model, dimension) -> embeddings(chunks));
        String text = "😀".repeat(3001);
        PublishedRagChunk chunk = new PublishedRagChunk(
                "processor-chunk-1", 0, text, text,
                new SourceProvenance(1, List.of("Chapter 1"), List.of(), null, "page=1"));

        RagExtractionVersion version = service.publish(request(
                List.of(new RagExtractionPage(1, "VALIDATED", 1, List.of())), List.of(chunk)));

        assertEquals(ExtractionPublicationStatus.INDEXED, version.status());
        assertEquals(1, repository.publishCalls);
    }

    @Test
    void successfulPublicationPassesAllProvenanceToOneAtomicRepositoryCall() {
        RecordingRepository repository = new RecordingRepository();
        List<PublishedRagChunk> chunks = List.of(new PublishedRagChunk(
                "processor-chunk-1", 0, "A rule", "A rule",
                new SourceProvenance(1, List.of("Chapter 1", "Checks"),
                        List.of(10d, 20d, 100d, 140d), "table-1:r2:c1", "page=1;block=b7")));
        RagExtractionPublicationService service = service(repository, (embeddingInputs, model, dimension) -> {
            assertEquals(requestDocument(), embeddingInputs.getFirst().rulebookId());
            return embeddings(embeddingInputs);
        });

        RagExtractionVersion published = service.publish(request(
                List.of(new RagExtractionPage(1, "VALIDATED", 1, List.of())), chunks));

        assertEquals(ExtractionPublicationStatus.INDEXED, published.status());
        assertEquals(1, repository.publishCalls);
        assertEquals("processor-chunk-1", repository.publishedChunks.getFirst().chunk().processorChunkId());
        assertEquals(List.of("Chapter 1", "Checks"), repository.publishedChunks.getFirst().chunk().provenance().sectionPath());
        assertEquals("table-1:r2:c1", repository.publishedChunks.getFirst().chunk().provenance().tableCell());
    }

    @Test
    void embeddingFailureMarksCandidateFailedAndLeavesPublicPointerUntouched() {
        RecordingRepository repository = new RecordingRepository();
        EmbeddingPort failing = (chunks, model, dimension) -> { throw new IllegalStateException("embedding down"); };
        RagExtractionPublicationService service = service(repository, failing);

        assertThrows(PublicationFailedException.class, () -> service.publish(request(
                List.of(new RagExtractionPage(1, "VALIDATED", 1, List.of())))));

        assertEquals(0, repository.publishCalls);
        assertEquals(1, repository.failedCalls);
        assertEquals(ExtractionPublicationStatus.FAILED, repository.failedStatus);
    }

    private static RagExtractionPublicationService service(
            RecordingRepository repository, EmbeddingPort embeddingPort) {
        return new RagExtractionPublicationService(repository, embeddingPort, "mock", 3);
    }

    private static RagExtractionPublicationRequest request(List<RagExtractionPage> pages) {
        return request(pages, List.of(new PublishedRagChunk(
                "processor-chunk-1", 0, "A rule", "A rule",
                new SourceProvenance(1, List.of("Chapter 1"), List.of(), null, "page=1"))));
    }

    private static RagExtractionPublicationRequest request(
            List<RagExtractionPage> pages, List<PublishedRagChunk> chunks) {
        return new RagExtractionPublicationRequest(
                REQUEST_DOCUMENT, new OwnerPlayerId(UUID.randomUUID()),
                "operation-1", "version-1", "a".repeat(64), "policy-1", "b".repeat(64),
                pages, chunks);
    }

    private static RulebookId requestDocument() {
        return REQUEST_DOCUMENT;
    }

    private static final RulebookId REQUEST_DOCUMENT = new RulebookId(UUID.fromString("00000000-0000-0000-0000-000000000015"));

    private static List<ChunkEmbedding> embeddings(List<RulebookChunk> chunks) {
        return chunks.stream()
                .map(chunk -> new ChunkEmbedding(chunk.chunkId(), new float[] {1f, 0f, 0f}))
                .toList();
    }

    private static java.util.Map<String, Integer> formEvidence() {
        return java.util.Map.of("drawing_count", 180, "aligned_line_count", 466,
                "long_horizontal_line_count", 154, "text_line_count", 15);
    }

    private static final class RecordingRepository implements RagExtractionPublicationRepository {
        private int publishCalls;
        private int failedCalls;
        private ExtractionPublicationStatus failedStatus;
        private List<EmbeddedPublishedRagChunk> publishedChunks = List.of();
        private RagExtractionPublicationRequest publishedRequest;

        @Override
        public void beginCandidate(RagExtractionPublicationRequest request) {}

        @Override
        public void publish(
                RagExtractionPublicationRequest request,
                List<EmbeddedPublishedRagChunk> chunks) {
            publishCalls++;
            publishedRequest = request;
            publishedChunks = List.copyOf(chunks);
        }

        @Override
        public void fail(
                RagExtractionPublicationRequest request,
                ExtractionPublicationStatus status,
                String reason) {
            failedCalls++;
            failedStatus = status;
        }
    }
}
