package com.dndmaster.ruleknowledge.application.indexing;

import com.dndmaster.ruleknowledge.domain.index.IndexKey;
import com.dndmaster.ruleknowledge.domain.index.RulebookIndex;
import java.util.function.Supplier;

public interface RulebookIndexRepository {
    RulebookIndex loadOrCreate(IndexKey key, Supplier<RulebookIndex> newIndex);

    void save(RulebookIndex index);
}
