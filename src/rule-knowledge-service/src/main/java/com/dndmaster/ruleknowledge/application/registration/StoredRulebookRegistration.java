package com.dndmaster.ruleknowledge.application.registration;

import com.dndmaster.ruleknowledge.application.preprocessing.PreprocessingPageState;
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
        Instant updatedAt,
        DocumentType documentType,
        String originalFilename,
        String previewContent,
        List<String> previewWarnings,
        List<PreviewSpan> previewSpans,
        List<PreviewAsset> previewAssets,
        String preprocessingOperationId,
        String candidateExtractionVersion,
        String preprocessingPolicyVersion,
        String preprocessingManifestSha256,
        List<PreprocessingPageState> preprocessingPages) {

    public StoredRulebookRegistration(
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
        this(rulebookId, ownerPlayerId, operationKey, contentHash, format, fileSize, storageKey,
                processingStatus, extractionStatus, extractedContent, missingLocations, failureCode,
                version, createdAt, updatedAt, DocumentType.RULEBOOK, "legacy-rulebook");
    }

    public StoredRulebookRegistration(
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
            Instant updatedAt,
            DocumentType documentType,
            String originalFilename) {
        this(
                rulebookId,
                ownerPlayerId,
                operationKey,
                contentHash,
                format,
                fileSize,
                storageKey,
                processingStatus,
                extractionStatus,
                extractedContent,
                missingLocations,
                failureCode,
                version,
                createdAt,
                updatedAt,
                documentType,
                originalFilename,
                extractedContent != null ? extractedContent : "",
                List.of(),
                List.of(),
                List.of(),
                null,
                null,
                null,
                null,
                List.of());
    }

    public StoredRulebookRegistration(
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
            Instant updatedAt,
            DocumentType documentType,
            String originalFilename,
            String previewContent,
            List<String> previewWarnings,
            List<PreviewSpan> previewSpans,
            List<PreviewAsset> previewAssets) {
        this(
                rulebookId, ownerPlayerId, operationKey, contentHash, format, fileSize, storageKey,
                processingStatus, extractionStatus, extractedContent, missingLocations, failureCode,
                version, createdAt, updatedAt, documentType, originalFilename, previewContent,
                previewWarnings, previewSpans, previewAssets, null, null, null, null, List.of());
    }

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
        documentType = DocumentType.require(documentType);
        if (originalFilename == null || originalFilename.isBlank()) {
            throw new IllegalArgumentException("original filename must not be blank");
        }
        originalFilename = originalFilename.trim();
        missingLocations = missingLocations == null ? List.of() : List.copyOf(missingLocations);
        previewContent = previewContent == null ? "" : previewContent;
        previewWarnings = previewWarnings == null ? List.of() : List.copyOf(previewWarnings);
        previewSpans = previewSpans == null ? List.of() : List.copyOf(previewSpans);
        previewAssets = previewAssets == null ? List.of() : List.copyOf(previewAssets);
        preprocessingPages = preprocessingPages == null ? List.of() : List.copyOf(preprocessingPages);
    }

    public KnowledgeDocumentId knowledgeDocumentId() {
        return KnowledgeDocumentId.fromRulebookId(rulebookId);
    }

    public SourcePreviewResult sourcePreviewResult() {
        return new SourcePreviewResult(previewContent, previewWarnings, previewSpans, previewAssets);
    }
}
