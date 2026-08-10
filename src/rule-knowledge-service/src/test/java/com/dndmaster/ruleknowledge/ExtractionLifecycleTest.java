package com.dndmaster.ruleknowledge;

import static org.junit.jupiter.api.Assertions.*;

import com.dndmaster.ruleknowledge.domain.rulebook.*;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class ExtractionLifecycleTest {
    @Test
    void publishedVersionIsImmutableAndRetainsSourceLineage() {
        KnowledgeDocument document = KnowledgeDocument.register(
                KnowledgeDocumentId.generate(), new OwnerPlayerId(java.util.UUID.randomUUID()),
                DocumentType.STORYBOOK, "story.pdf", RulebookFormat.PDF, 10, "hash");
        ExtractionVersion draft = document.startExtraction(1);
        SourceSpan span = new SourceSpan(1, 0, 5, "hello", "page:1", 1, new BoundingBox(1, 2, 3, 4), 0);
        draft.addSourceSpan(span);
        draft.beginValidation();
        ExtractionVersion published = document.publish(draft);

        assertEquals(1, published.version());
        assertEquals(List.of(span), published.sourceSpans());
        assertSame(published, document.currentPublishedVersion().orElseThrow());
        assertThrows(UnsupportedOperationException.class, () -> published.sourceSpans().add(span));
        assertThrows(IllegalStateException.class, () -> published.addSourceSpan(span));
        assertThrows(IllegalStateException.class, () -> document.publish(published));
    }

    @Test
    void publicationRejectsVersionBuiltFromDifferentSourceHash() {
        KnowledgeDocument document = document();
        document.startExtraction(1);
        ExtractionVersion version = new ExtractionVersion(document.metadata().id(), 1, "different-hash");
        version.addSourceSpan(new SourceSpan(1, 0, 5, "hello", "page:1"));

        assertThrows(IllegalArgumentException.class, () -> document.publish(version));
    }

    @Test
    void processingJobLeaseFailureAndRetryArePerDocument() {
        KnowledgeDocument first = document();
        KnowledgeDocument second = document();
        DocumentProcessingJob job = first.startJob();
        assertEquals(DocumentProcessingJob.Status.PROCESSING, job.claim("worker-a", Duration.ofMinutes(5), Instant.now()));
        String leaseToken = job.leaseToken();
        assertThrows(IllegalArgumentException.class, () -> job.fail("wrong-token", "OCR_TIMEOUT", Instant.now()));
        job.fail(leaseToken, "OCR_TIMEOUT", Instant.now());
        assertEquals(DocumentProcessingJob.Status.FAILED, job.status());
        assertNull(job.leaseToken());
        DocumentProcessingJob retry = job.retry(Instant.now());
        assertNotEquals(job.attempt(), retry.attempt());
        assertEquals(DocumentProcessingJob.Status.QUEUED, retry.status());
        assertEquals(KnowledgeDocument.Status.REGISTERED, second.status());

        DocumentProcessingJob completed = second.startJob();
        completed.claim("worker-b", Duration.ofMinutes(5), Instant.now());
        completed.complete(completed.leaseToken(), Instant.now());
        assertNull(completed.leaseToken());
    }

    private static KnowledgeDocument document() {
        return KnowledgeDocument.register(KnowledgeDocumentId.generate(),
                new OwnerPlayerId(java.util.UUID.randomUUID()), DocumentType.RULEBOOK,
                "rules.pdf", RulebookFormat.PDF, 1, java.util.UUID.randomUUID().toString());
    }
}
