package com.dndmaster.ruleknowledge.domain.rulebook;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class DocumentProcessingJob {
    public enum Status { QUEUED, PROCESSING, FAILED, COMPLETED }

    private final KnowledgeDocumentId documentId;
    private final int attempt;
    private Status status;
    private String leaseToken;
    private Instant leaseExpiresAt;
    private String failureCode;

    public DocumentProcessingJob(KnowledgeDocumentId documentId) {
        this(documentId, 1, Status.QUEUED);
    }

    private DocumentProcessingJob(KnowledgeDocumentId documentId, int attempt, Status status) {
        this.documentId = Objects.requireNonNull(documentId, "documentId must not be null");
        this.attempt = attempt;
        this.status = status;
    }

    public Status claim(String worker, Duration lease, Instant now) {
        Objects.requireNonNull(worker, "worker must not be null");
        if (worker.isBlank() || lease.isZero() || lease.isNegative()) throw new IllegalArgumentException("worker and lease must be valid");
        Objects.requireNonNull(now, "now must not be null");
        if (status != Status.QUEUED && !(status == Status.PROCESSING && leaseExpiresAt != null && !leaseExpiresAt.isAfter(now))) {
            throw new IllegalStateException("job is not claimable");
        }
        status = Status.PROCESSING;
        leaseToken = worker + ":" + UUID.randomUUID();
        leaseExpiresAt = now.plus(lease);
        return status;
    }

    public void fail(String code, Instant now) {
        requireProcessing(now);
        if (code == null || code.isBlank()) throw new IllegalArgumentException("failure code must not be blank");
        status = Status.FAILED;
        failureCode = code.trim();
        leaseToken = null;
        leaseExpiresAt = null;
    }

    public DocumentProcessingJob retry(Instant now) {
        if (status != Status.FAILED) throw new IllegalStateException("only failed job can be retried");
        Objects.requireNonNull(now, "now must not be null");
        return new DocumentProcessingJob(documentId, attempt + 1, Status.QUEUED);
    }

    public void complete(Instant now) {
        requireProcessing(now);
        status = Status.COMPLETED;
        leaseToken = null;
        leaseExpiresAt = null;
    }

    public KnowledgeDocumentId documentId() { return documentId; }
    public int attempt() { return attempt; }
    public Status status() { return status; }
    public String leaseToken() { return leaseToken; }
    public Instant leaseExpiresAt() { return leaseExpiresAt; }
    public String failureCode() { return failureCode; }

    private void requireProcessing(Instant now) {
        Objects.requireNonNull(now, "now must not be null");
        if (status != Status.PROCESSING || leaseExpiresAt == null || !leaseExpiresAt.isAfter(now)) throw new IllegalStateException("job lease is not active");
    }
}
