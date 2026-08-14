package com.dndmaster.ruleknowledge.application.registration;

import com.dndmaster.ruleknowledge.domain.rulebook.RulebookId;

public interface RulebookFileStorage {
    StoredRulebookFile store(RulebookId rulebookId, byte[] content);

    byte[] read(StoredRulebookFile storedFile);
    default void delete(StoredRulebookFile storedFile) {
        throw new UnsupportedOperationException("deleting stored rulebook files is not supported");
    }
}
