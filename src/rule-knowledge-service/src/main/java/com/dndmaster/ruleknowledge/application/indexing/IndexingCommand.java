package com.dndmaster.ruleknowledge.application.indexing;

import com.dndmaster.ruleknowledge.domain.index.IndexKey;
import com.dndmaster.ruleknowledge.domain.rulebook.Rulebook;
import java.util.Objects;

public record IndexingCommand(Rulebook rulebook, IndexKey key, int dimension) {
    public IndexingCommand {
        Objects.requireNonNull(rulebook, "rulebook must not be null");
        Objects.requireNonNull(key, "key must not be null");
        if (dimension <= 0) throw new IllegalArgumentException("dimension must be positive");
    }
}
