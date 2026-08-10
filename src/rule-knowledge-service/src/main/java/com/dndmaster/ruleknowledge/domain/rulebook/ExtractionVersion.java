package com.dndmaster.ruleknowledge.domain.rulebook;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Append-only source-derived snapshot. Published instances cannot be changed. */
public final class ExtractionVersion {
    public enum Status { DRAFT, VALIDATING, PUBLISHED, REJECTED }

    private final KnowledgeDocumentId documentId;
    private final long version;
    private final String contentHash;
    private final List<SourceSpan> sourceSpans = new ArrayList<>();
    private Status status = Status.DRAFT;
    private Instant publishedAt;

    public ExtractionVersion(KnowledgeDocumentId documentId, long version, String contentHash) {
        this.documentId = Objects.requireNonNull(documentId, "documentId must not be null");
        if (version <= 0) throw new IllegalArgumentException("version must be positive");
        this.version = version;
        if (contentHash == null || contentHash.isBlank()) throw new IllegalArgumentException("contentHash must not be blank");
        this.contentHash = contentHash.trim();
    }

    public void addSourceSpan(SourceSpan span) {
        ensureDraft();
        sourceSpans.add(Objects.requireNonNull(span, "span must not be null"));
    }

    public void beginValidation() {
        ensureDraft();
        if (sourceSpans.isEmpty()) throw new IllegalStateException("validation requires source spans");
        status = Status.VALIDATING;
    }

    public ExtractionVersion publish(Instant at) {
        if (status != Status.VALIDATING) throw new IllegalStateException("version must be validating before publication");
        status = Status.PUBLISHED;
        publishedAt = Objects.requireNonNull(at, "publishedAt must not be null");
        return this;
    }

    public void reject() {
        if (status != Status.VALIDATING) throw new IllegalStateException("only validating version can be rejected");
        status = Status.REJECTED;
    }

    public KnowledgeDocumentId documentId() { return documentId; }
    public long version() { return version; }
    public String contentHash() { return contentHash; }
    public List<SourceSpan> sourceSpans() { return List.copyOf(sourceSpans); }
    public Status status() { return status; }
    public Instant publishedAt() { return publishedAt; }

    private void ensureDraft() {
        if (status != Status.DRAFT) throw new IllegalStateException("non-draft extraction version is immutable");
    }
}
