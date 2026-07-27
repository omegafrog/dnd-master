package com.dndmaster.character.application;

import com.dndmaster.character.domain.CharacterSheet;
import com.dndmaster.character.domain.CharacterSheetId;
import java.util.Optional;
import java.util.UUID;

public interface CharacterSheetRepository {
    Optional<CharacterSheet> findById(CharacterSheetId id);
    Optional<CharacterSheet> findByCommandId(UUID commandId);
    void save(CharacterSheet sheet);
    void save(CharacterSheet sheet, long persistedVersion, UUID operationKey, String operationFingerprint);
    default void deleteById(CharacterSheetId id) {}
}
