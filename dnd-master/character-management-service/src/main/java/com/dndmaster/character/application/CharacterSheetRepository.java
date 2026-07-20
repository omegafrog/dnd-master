package com.dndmaster.character.application;

import com.dndmaster.character.domain.CharacterSheet;
import com.dndmaster.character.domain.CharacterSheetId;
import java.util.Optional;

public interface CharacterSheetRepository {
    Optional<CharacterSheet> findById(CharacterSheetId id);
    void save(CharacterSheet sheet);
}
