package com.dndmaster.ruleknowledge.application.registration;

import com.dndmaster.ruleknowledge.domain.rulebook.*;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record StoredRulebookRegistration(
        RulebookId rulebookId,
        OwnerPlayerId ownerPlayerId,
        String operationKey,
        String contentHash,
        RulebookFormat format,
        long fileSize,
        String storageKey,
        ProcessingStatus processingStatus,
        ExtractionStatus extractionStatus,
        String extractedContent,
        List<String> missingLocations,
        String failureCode,
        long version,
        Instant createdAt,
        Instant updatedAt) {

    public StoredRulebookRegistration {
        Objects.requireNonNull(rulebookId, "rulebookId must not be null");
        Objects.requireNonNull(ownerPlayerId, "ownerPlayerId must not be null");
        Objects.requireNonNull(operationKey, "operationKey must not be null");
        Objects.requireNonNull(contentHash, "contentHash must not be null");
        Objects.requireNonNull(format, "format must not be null");
        if (fileSize <= 0) throw new IllegalArgumentException("fileSize must be positive");
        Objects.requireNonNull(storageKey, "storageKey must not be null");
        Objects.requireNonNull(processingStatus, "processingStatus must not be null");
        if (version < 0) throw new IllegalArgumentException("version must not be negative");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        Objects.requireNonNull(updatedAt, "updatedAt must not be null");
        missingLocations = missingLocations == null ? List.of() : List.copyOf(missingLocations);
    }
}
