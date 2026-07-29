package com.dndmaster.ruleknowledge.application.indexing;

import com.dndmaster.ruleknowledge.domain.index.IndexKey;
import com.dndmaster.ruleknowledge.domain.index.RulebookIndex;
import com.dndmaster.ruleknowledge.domain.index.EmbeddedRulebookChunk;
import java.util.List;
import java.util.function.Supplier;

public interface RulebookIndexRepository {
    RulebookIndex loadOrCreate(IndexKey key, Supplier<RulebookIndex> newIndex);
    void save(RulebookIndex index);
    void saveBatch(RulebookIndex index, List<EmbeddedRulebookChunk> chunks, int totalChunks, int completedChunks);
    void saveComplete(RulebookIndex index, List<EmbeddedRulebookChunk> chunks);
}
