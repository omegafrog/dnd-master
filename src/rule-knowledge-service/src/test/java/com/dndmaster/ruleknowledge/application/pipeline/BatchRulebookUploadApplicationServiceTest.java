package com.dndmaster.ruleknowledge.application.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dndmaster.ruleknowledge.domain.rulebook.DocumentType;
import com.dndmaster.ruleknowledge.domain.rulebook.OwnerPlayerId;
import com.dndmaster.ruleknowledge.domain.rulebook.ProcessingStatus;
import com.dndmaster.ruleknowledge.domain.rulebook.RulebookFormat;
import com.dndmaster.ruleknowledge.domain.rulebook.RulebookId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BatchRulebookUploadApplicationServiceTest {
    @Test
    void mixedDocumentTypesReachPipelineAndReturnPerItemResults() {
        CapturingPipeline pipeline = new CapturingPipeline();
        BatchRulebookUploadApplicationService service = new BatchRulebookUploadApplicationService(pipeline);

        List<BatchRulebookUploadApplicationService.BatchUploadItem> items = List.of(
                new BatchRulebookUploadApplicationService.BatchUploadItem(
                        "key-1", owner(), DocumentType.RULEBOOK, RulebookFormat.PDF, "rules.pdf", "rules".getBytes()),
                new BatchRulebookUploadApplicationService.BatchUploadItem(
                        "key-2", owner(), DocumentType.STORYBOOK, RulebookFormat.TXT, "story.txt", "story".getBytes()));

        List<BatchRulebookUploadApplicationService.BatchUploadResult> results = service.process(items);

        assertEquals(2, results.size());
        assertEquals(DocumentType.RULEBOOK, pipeline.commands.get(0).documentType());
        assertEquals(DocumentType.STORYBOOK, pipeline.commands.get(1).documentType());
        assertEquals("ACCEPTED", results.get(0).status());
        assertEquals("ACCEPTED", results.get(1).status());
    }

    @Test
    void oneValidationFailureDoesNotRollbackAcceptedItems() {
        CapturingPipeline pipeline = new CapturingPipeline();
        pipeline.failOnSecond = true;
        BatchRulebookUploadApplicationService service = new BatchRulebookUploadApplicationService(pipeline);

        List<BatchRulebookUploadApplicationService.BatchUploadItem> items = List.of(
                new BatchRulebookUploadApplicationService.BatchUploadItem(
                        "key-1", owner(), DocumentType.RULEBOOK, RulebookFormat.PDF, "rules.pdf", "rules".getBytes()),
                new BatchRulebookUploadApplicationService.BatchUploadItem(
                        "key-2", owner(), DocumentType.STORYBOOK, RulebookFormat.TXT, "story.txt", "story".getBytes()));

        List<BatchRulebookUploadApplicationService.BatchUploadResult> results = service.process(items);

        assertEquals("ACCEPTED", results.get(0).status());
        assertEquals("VALIDATION_FAILED", results.get(1).status());
        assertTrue(results.get(1).failureReason().contains("invalid type"));
    }

    @Test
    void blankIdempotencyKeysBecomeDistinctPerItem() {
        CapturingPipeline pipeline = new CapturingPipeline();
        BatchRulebookUploadApplicationService service = new BatchRulebookUploadApplicationService(pipeline);

        service.process(List.of(
                new BatchRulebookUploadApplicationService.BatchUploadItem(
                        "", owner(), DocumentType.RULEBOOK, RulebookFormat.PDF, "one.pdf", "1".getBytes()),
                new BatchRulebookUploadApplicationService.BatchUploadItem(
                        " ", owner(), DocumentType.RULEBOOK, RulebookFormat.PDF, "two.pdf", "2".getBytes())));

        assertEquals(2, pipeline.commands.size());
        assertTrue(!pipeline.commands.get(0).operationKey().isBlank());
        assertTrue(!pipeline.commands.get(1).operationKey().isBlank());
        assertNotEquals(pipeline.commands.get(0).operationKey(), pipeline.commands.get(1).operationKey());
    }

    private static OwnerPlayerId owner() {
        return new OwnerPlayerId(UUID.randomUUID());
    }

    private static final class CapturingPipeline implements RulebookUploadProcessor {
        final List<UploadRulebookCommand> commands = new ArrayList<>();
        boolean failOnSecond;

        @Override
        public RulebookProcessingResult process(UploadRulebookCommand command) {
            commands.add(command);
            if (failOnSecond && commands.size() == 2) {
                throw new IllegalArgumentException("invalid type");
            }
            return new RulebookProcessingResult(new RulebookId(UUID.randomUUID()), ProcessingStatus.INDEXED, List.of());
        }
    }
}
