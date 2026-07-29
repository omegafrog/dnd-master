package com.dndmaster.ruleknowledge.application.indexing;

import com.dndmaster.ruleknowledge.domain.index.IndexKey;
import com.dndmaster.ruleknowledge.domain.index.RulebookIndex;
import com.dndmaster.ruleknowledge.domain.index.EmbeddedRulebookChunk;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

public interface RulebookIndexRepository {
    RulebookIndex loadOrCreate(IndexKey key, Supplier<RulebookIndex> newIndex);
    void save(RulebookIndex index);
    default void saveBatch(RulebookIndex index, List<EmbeddedRulebookChunk> chunks) {
        Objects.requireNonNull(index, "index must not be null");
        Objects.requireNonNull(chunks, "chunks must not be null");
    }
    void saveComplete(RulebookIndex index, List<EmbeddedRulebookChunk> chunks);
}
