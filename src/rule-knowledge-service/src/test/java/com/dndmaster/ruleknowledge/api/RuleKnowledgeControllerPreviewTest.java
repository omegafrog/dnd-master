package com.dndmaster.ruleknowledge.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

import com.dndmaster.ruleknowledge.application.registration.StoredRulebookRegistration;
import com.dndmaster.ruleknowledge.application.search.RuleEvidenceSearchApplicationService;
import com.dndmaster.ruleknowledge.application.registration.RulebookRegistrationRepository;
import com.dndmaster.ruleknowledge.domain.rulebook.DocumentType;
import com.dndmaster.ruleknowledge.domain.rulebook.ExtractionStatus;
import com.dndmaster.ruleknowledge.domain.rulebook.OwnerPlayerId;
import com.dndmaster.ruleknowledge.domain.rulebook.PreviewAsset;
import com.dndmaster.ruleknowledge.domain.rulebook.PreviewSpan;
import com.dndmaster.ruleknowledge.domain.rulebook.ProcessingStatus;
import com.dndmaster.ruleknowledge.domain.rulebook.RulebookFormat;
import com.dndmaster.ruleknowledge.domain.rulebook.RulebookId;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

class RuleKnowledgeControllerPreviewTest {

    @Test
    void sourcePreviewReadsStoredSnapshotOnly() {
        RulebookId rulebookId = RulebookId.generate();
        StoredRulebookRegistration registration = new StoredRulebookRegistration(
                rulebookId,
                new OwnerPlayerId(UUID.randomUUID()),
                "op-1",
                "hash-1",
                RulebookFormat.PDF,
                42,
                "storage-1",
                ProcessingStatus.INDEXED,
                ExtractionStatus.SUCCESS,
                "extracted content",
                List.of("page 9"),
                null,
                1L,
                Instant.parse("2026-07-23T00:00:00Z"),
                Instant.parse("2026-07-23T00:01:00Z"),
                DocumentType.RULEBOOK,
                "rules.pdf",
                "stored preview content",
                List.of("stored warning"),
                List.of(new PreviewSpan("PAGE_LINE", List.of("page 1"), 1, null, 1, 0, 7, "preview", "page 1 line 1", "TEXT", null)),
                List.of(new PreviewAsset("IMAGE", "page 1 image 1", "image/png", 1)));

        RuleKnowledgeController controller = new RuleKnowledgeController(
                mock(com.dndmaster.ruleknowledge.application.pipeline.RulebookPipelineApplicationService.class),
                new InMemoryRegistrationRepository(registration),
                mock(RuleEvidenceSearchApplicationService.class),
                new com.fasterxml.jackson.databind.ObjectMapper());

        ResponseEntity<RuleKnowledgeController.SourcePreviewResponse> response = controller.sourcePreview(rulebookId.value());

        assertEquals("stored preview content", response.getBody().content());
        assertEquals(List.of("page 9", "stored warning"), response.getBody().warnings());
        assertEquals(1, response.getBody().spans().size());
        assertEquals(1, response.getBody().assets().size());
    }

    private static final class InMemoryRegistrationRepository implements RulebookRegistrationRepository {
        private final StoredRulebookRegistration registration;

        private InMemoryRegistrationRepository(StoredRulebookRegistration registration) {
            this.registration = registration;
        }

        @Override
        public java.util.Optional<StoredRulebookRegistration> findById(RulebookId id) {
            return registration.rulebookId().equals(id) ? java.util.Optional.of(registration) : java.util.Optional.empty();
        }

        @Override
        public java.util.Optional<StoredRulebookRegistration> findByOperationKey(String operationKey) {
            return java.util.Optional.empty();
        }

        @Override
        public java.util.Optional<StoredRulebookRegistration> findByOwnerAndContentHash(
                OwnerPlayerId owner, String contentHash) {
            return java.util.Optional.empty();
        }

        @Override
        public java.util.List<StoredRulebookRegistration> findByOwner(OwnerPlayerId owner) {
            return java.util.List.of();
        }

        @Override
        public java.util.List<StoredRulebookRegistration> findByProcessingStatuses(
                java.util.List<com.dndmaster.ruleknowledge.domain.rulebook.ProcessingStatus> statuses) {
            return java.util.List.of();
        }

        @Override
        public java.util.List<StoredRulebookRegistration> claimPending(java.time.Instant processingLeaseCutoff, int limit) {
            return java.util.List.of();
        }

        @Override
        public void save(StoredRulebookRegistration registration) {
        }
    }
}
