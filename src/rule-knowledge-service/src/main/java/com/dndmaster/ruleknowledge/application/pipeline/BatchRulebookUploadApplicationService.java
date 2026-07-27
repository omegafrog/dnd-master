package com.dndmaster.ruleknowledge.application.pipeline;

import com.dndmaster.ruleknowledge.domain.rulebook.DocumentType;
import com.dndmaster.ruleknowledge.domain.rulebook.OwnerPlayerId;
import com.dndmaster.ruleknowledge.domain.rulebook.RulebookFormat;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class BatchRulebookUploadApplicationService {
    private final RulebookUploadProcessor processor;

    public BatchRulebookUploadApplicationService(RulebookUploadProcessor processor) {
        this.processor = Objects.requireNonNull(processor, "processor must not be null");
    }

    public List<BatchUploadResult> process(List<BatchUploadItem> items) {
        Objects.requireNonNull(items, "items must not be null");
        return items.stream().map(this::processOne).toList();
    }

    private BatchUploadResult processOne(BatchUploadItem item) {
        Objects.requireNonNull(item, "item must not be null");
        try {
            RulebookProcessingResult result = processor.process(new UploadRulebookCommand(
                    item.operationKey(),
                    item.ownerPlayerId(),
                    item.documentType(),
                    item.format(),
                    item.fileContent(),
                    item.originalFilename()));
            return new BatchUploadResult(
                    result.rulebookId().value(),
                    item.documentType(),
                    item.originalFilename(),
                    "ACCEPTED",
                    null);
        } catch (Exception exception) {
            return new BatchUploadResult(
                    null,
                    item.documentType(),
                    item.originalFilename(),
                    "VALIDATION_FAILED",
                    exception.getMessage() != null ? exception.getMessage() : "upload failed");
        }
    }

    public record BatchUploadItem(
            String operationKey,
            OwnerPlayerId ownerPlayerId,
            DocumentType documentType,
            RulebookFormat format,
            String originalFilename,
            byte[] fileContent) {}

    public record BatchUploadResult(
            UUID knowledgeDocumentId,
            DocumentType documentType,
            String originalFilename,
            String status,
            String failureReason) {}
}
