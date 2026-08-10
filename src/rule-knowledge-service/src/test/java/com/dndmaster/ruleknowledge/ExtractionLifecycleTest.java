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
        ExtractionVersion published = document.publish(draft);

        assertEquals(1, published.version());
        assertEquals(List.of(span), published.sourceSpans());
        assertSame(published, document.currentPublishedVersion().orElseThrow());
        assertThrows(UnsupportedOperationException.class, () -> published.sourceSpans().add(span));
        assertThrows(IllegalStateException.class, () -> published.addSourceSpan(span));
        assertThrows(IllegalStateException.class, () -> document.publish(published));
    }

    @Test
    void processingJobLeaseFailureAndRetryArePerDocument() {
        KnowledgeDocument first = document();
        KnowledgeDocument second = document();
        DocumentProcessingJob job = first.startJob();
        assertEquals(DocumentProcessingJob.Status.PROCESSING, job.claim("worker-a", Duration.ofMinutes(5), Instant.now()));
        job.fail("OCR_TIMEOUT", Instant.now());
        assertEquals(DocumentProcessingJob.Status.FAILED, job.status());
        DocumentProcessingJob retry = job.retry(Instant.now());
        assertNotEquals(job.attempt(), retry.attempt());
        assertEquals(DocumentProcessingJob.Status.QUEUED, retry.status());
        assertEquals(KnowledgeDocument.Status.REGISTERED, second.status());
    }

    private static KnowledgeDocument document() {
        return KnowledgeDocument.register(KnowledgeDocumentId.generate(),
                new OwnerPlayerId(java.util.UUID.randomUUID()), DocumentType.RULEBOOK,
                "rules.pdf", RulebookFormat.PDF, 1, java.util.UUID.randomUUID().toString());
    }
}
