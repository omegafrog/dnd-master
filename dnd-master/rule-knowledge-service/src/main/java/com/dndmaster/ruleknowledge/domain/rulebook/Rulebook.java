package com.dndmaster.ruleknowledge.domain.rulebook;

import java.util.Objects;
import java.util.Optional;

public final class Rulebook {
    private final RulebookId id;
    private final OwnerPlayerId ownerPlayerId;
    private final RulebookFormat format;
    private final FileSize fileSize;
    private ProcessingStatus processingStatus;
    private ExtractionResult extractionResult;

    private Rulebook(RulebookId id, OwnerPlayerId ownerPlayerId, RulebookFormat format, FileSize fileSize) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.ownerPlayerId = Objects.requireNonNull(ownerPlayerId, "ownerPlayerId must not be null");
        this.format = Objects.requireNonNull(format, "format must not be null");
        this.fileSize = Objects.requireNonNull(fileSize, "fileSize must not be null");
        this.processingStatus = ProcessingStatus.UPLOADED;
    }

    public static Rulebook acceptUpload(
            RulebookId id, OwnerPlayerId ownerPlayerId, RulebookFormat format, FileSize fileSize) {
        return new Rulebook(id, ownerPlayerId, format, fileSize);
    }

    public void recordExtraction(ExtractionResult result) {
        if (processingStatus != ProcessingStatus.UPLOADED) {
            throw new IllegalStateException("extraction can only be recorded once after upload");
        }
        extractionResult = Objects.requireNonNull(result, "result must not be null");
        processingStatus = switch (result.status()) {
            case SUCCESS -> ProcessingStatus.EXTRACTED;
            case PARTIAL -> ProcessingStatus.PARTIAL_AWAITING_CONFIRMATION;
            case FAILED -> ProcessingStatus.REJECTED;
        };
    }

    public void confirmPartialExtraction() {
        if (processingStatus != ProcessingStatus.PARTIAL_AWAITING_CONFIRMATION) {
            throw new IllegalStateException("rulebook is not awaiting partial extraction confirmation");
        }
        extractionResult = extractionResult.confirmPartial();
        processingStatus = ProcessingStatus.PARTIAL_CONFIRMED;
    }

    public boolean isEligibleForSplitting() {
        return processingStatus == ProcessingStatus.EXTRACTED
                || processingStatus == ProcessingStatus.PARTIAL_CONFIRMED;
    }

    public RulebookId id() { return id; }
    public OwnerPlayerId ownerPlayerId() { return ownerPlayerId; }
    public RulebookFormat format() { return format; }
    public FileSize fileSize() { return fileSize; }
    public ProcessingStatus processingStatus() { return processingStatus; }
    public Optional<ExtractionResult> extractionResult() { return Optional.ofNullable(extractionResult); }
}
