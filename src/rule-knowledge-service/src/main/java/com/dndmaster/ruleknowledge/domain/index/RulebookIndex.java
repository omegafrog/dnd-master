package com.dndmaster.ruleknowledge.domain.index;

import com.dndmaster.ruleknowledge.domain.rulebook.OwnerPlayerId;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.HashSet;

public final class RulebookIndex {
    private final IndexId id;
    private final IndexKey key;
    private final OwnerPlayerId ownerPlayerId;
    private final int dimension;
    private IndexStatus status;
    private int attempts;
    private long version;
    private String failureReason;
    private List<RulebookChunk> chunks;

    RulebookIndex(IndexId id, IndexKey key, OwnerPlayerId ownerPlayerId, int dimension) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.key = Objects.requireNonNull(key, "key must not be null");
        this.ownerPlayerId = Objects.requireNonNull(ownerPlayerId, "ownerPlayerId must not be null");
        if (dimension <= 0) throw new IllegalArgumentException("dimension must be positive");
        this.dimension = dimension;
        this.status = IndexStatus.PENDING;
        this.chunks = List.of();
    }

    public static RulebookIndex rehydrate(
            IndexId id,
            IndexKey key,
            OwnerPlayerId ownerPlayerId,
            int dimension,
            IndexStatus status,
            int attempts,
            long version,
            String failureReason) {
        RulebookIndex index = new RulebookIndex(id, key, ownerPlayerId, dimension);
        index.status = Objects.requireNonNull(status, "status must not be null");
        if (attempts < 0) throw new IllegalArgumentException("attempts must not be negative");
        if (version < 0) throw new IllegalArgumentException("version must not be negative");
        index.attempts = attempts;
        index.version = version;
        index.failureReason = failureReason;
        return index;
    }

    public void beginAttempt() {
        if (status != IndexStatus.PENDING && status != IndexStatus.RETRYABLE_FAILURE) {
            throw new IllegalStateException("only pending or failed index can begin an attempt");
        }
        status = IndexStatus.EMBEDDING;
        attempts++;
        version++;
        failureReason = null;
    }

    public void complete(List<RulebookChunk> completedChunks) {
        if (status != IndexStatus.EMBEDDING) throw new IllegalStateException("index is not embedding");
        Objects.requireNonNull(completedChunks, "completedChunks must not be null");
        if (completedChunks.isEmpty()) throw new IllegalArgumentException("completed index requires chunks");
        if (completedChunks.stream().anyMatch(chunk -> !chunk.rulebookId().equals(key.rulebookId()))) {
            throw new IllegalArgumentException("all chunks must belong to the indexed rulebook");
        }
        var chunkIds = new HashSet<ChunkId>();
        var sequences = new HashSet<Integer>();
        for (int position = 0; position < completedChunks.size(); position++) {
            RulebookChunk chunk = Objects.requireNonNull(
                    completedChunks.get(position), "completed chunks must not contain null");
            if (!chunkIds.add(chunk.chunkId())) {
                throw new IllegalArgumentException("completed chunks must have unique ids");
            }
            if (!sequences.add(chunk.sequence()) || chunk.sequence() != position) {
                throw new IllegalArgumentException("completed chunks must have unique contiguous sequences");
            }
        }
        chunks = List.copyOf(completedChunks);
        status = IndexStatus.READY;
        version++;
    }

    public void fail(String reason, boolean retryable) {
        if (status != IndexStatus.EMBEDDING) throw new IllegalStateException("index is not embedding");
        if (reason == null || reason.isBlank()) throw new IllegalArgumentException("failure reason must not be blank");
        failureReason = reason.trim();
        chunks = List.of();
        status = retryable ? IndexStatus.RETRYABLE_FAILURE : IndexStatus.PERMANENT_FAILURE;
        version++;
    }

    public IndexId id() { return id; }
    public IndexKey key() { return key; }
    public OwnerPlayerId ownerPlayerId() { return ownerPlayerId; }
    public int dimension() { return dimension; }
    public IndexStatus status() { return status; }
    public int attempts() { return attempts; }
    public long version() { return version; }
    public Optional<String> failureReason() { return Optional.ofNullable(failureReason); }
    public List<RulebookChunk> chunks() { return chunks; }
}
