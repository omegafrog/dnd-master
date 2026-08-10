package com.dndmaster.ruleknowledge.domain.rulebook;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public final class KnowledgeDocument {
    public enum Status { REGISTERED, PROCESSING, FAILED, PUBLISHED }

    private final KnowledgeDocumentMetadata metadata;
    private final String contentHash;
    private long latestVersion;
    private Status status = Status.REGISTERED;
    private ExtractionVersion currentPublishedVersion;

    private KnowledgeDocument(KnowledgeDocumentMetadata metadata, String contentHash) {
        this.metadata = metadata;
        this.contentHash = contentHash;
    }

    public static KnowledgeDocument register(KnowledgeDocumentId id, OwnerPlayerId owner, DocumentType type,
            String filename, RulebookFormat format, long fileSize, String contentHash) {
        if (contentHash == null || contentHash.isBlank()) throw new IllegalArgumentException("contentHash must not be blank");
        return new KnowledgeDocument(new KnowledgeDocumentMetadata(id, owner, type, filename, format, fileSize), contentHash.trim());
    }

    public ExtractionVersion startExtraction(long version) {
        if (version <= latestVersion) throw new IllegalArgumentException("extraction version must increase");
        latestVersion = version;
        status = Status.PROCESSING;
        return new ExtractionVersion(metadata.id(), version, contentHash);
    }

    public ExtractionVersion publish(ExtractionVersion version) {
        Objects.requireNonNull(version, "version must not be null");
        if (version.documentId().equals(metadata.id()) == false) throw new IllegalArgumentException("version belongs to another document");
        if (version.version() != latestVersion
                || (version.status() != ExtractionVersion.Status.DRAFT
                    && version.status() != ExtractionVersion.Status.VALIDATING)) {
            throw new IllegalStateException("version is not current draft");
        }
        if (!contentHash.equals(version.contentHash())) {
            throw new IllegalArgumentException("version content hash does not match document source");
        }
        if (version.status() == ExtractionVersion.Status.DRAFT) version.beginValidation();
        version.publish(Instant.now());
        currentPublishedVersion = version;
        status = Status.PUBLISHED;
        return version;
    }

    public DocumentProcessingJob startJob() { status = Status.PROCESSING; return new DocumentProcessingJob(metadata.id()); }
    public KnowledgeDocumentMetadata metadata() { return metadata; }
    public String contentHash() { return contentHash; }
    public Status status() { return status; }
    public Optional<ExtractionVersion> currentPublishedVersion() { return Optional.ofNullable(currentPublishedVersion); }
}
