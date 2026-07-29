package com.dndmaster.ruleknowledge.application.indexing;

import com.dndmaster.ruleknowledge.domain.index.IndexKey;
import com.dndmaster.ruleknowledge.domain.index.RulebookIndex;
import com.dndmaster.ruleknowledge.domain.index.EmbeddedRulebookChunk;
import java.util.List;
import java.util.Set;
import java.util.Optional;
import java.time.Duration;
import java.time.Instant;
import com.dndmaster.ruleknowledge.domain.rulebook.RulebookId;
import java.util.function.Supplier;

public interface RulebookIndexRepository {
    RulebookIndex loadOrCreate(IndexKey key, Supplier<RulebookIndex> newIndex);
    void save(RulebookIndex index);
    default void save(RulebookIndex index, IndexLease lease) { save(index); }
    void saveBatch(RulebookIndex index, List<EmbeddedRulebookChunk> chunks, int totalChunks, int completedChunks);
    default void saveBatch(
            RulebookIndex index, List<EmbeddedRulebookChunk> chunks, int totalChunks, int completedChunks, IndexLease lease) {
        saveBatch(index, chunks, totalChunks, completedChunks);
    }
    Set<Integer> completedSequences(RulebookIndex index);
    default Optional<IndexProgress> progressFor(RulebookId rulebookId) { return Optional.empty(); }
    default Optional<IndexProgress> progressFor(RulebookId rulebookId, String indexVersion) {
        return progressFor(rulebookId);
    }
    default Optional<IndexLease> claimLease(IndexKey key, String owner, String token, Instant now, Duration duration) {
        return Optional.empty();
    }
    default Optional<IndexLease> claimLease(
            RulebookIndex index, String owner, String token, Instant now, Duration duration) {
        return Optional.of(new IndexLease(index.id(), owner, token, now.plus(duration)));
    }
    default boolean renewLease(IndexLease lease, Instant now, Duration duration) { return true; }
    default boolean releaseLease(IndexLease lease) { return true; }
    void saveComplete(RulebookIndex index, List<EmbeddedRulebookChunk> chunks);
    default void saveComplete(RulebookIndex index, List<EmbeddedRulebookChunk> chunks, IndexLease lease) {
        saveComplete(index, chunks);
    }
}
