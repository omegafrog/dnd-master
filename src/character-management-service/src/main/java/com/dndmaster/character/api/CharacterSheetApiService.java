package com.dndmaster.character.api;

import com.dndmaster.character.application.CharacterSheetNotFoundException;
import com.dndmaster.character.application.CharacterSheetRepository;
import com.dndmaster.character.domain.AdventureId;
import com.dndmaster.character.domain.CharacterSheetId;
import java.util.Objects;

public final class CharacterSheetApiService {
    private final CharacterSheetRepository repository;

    public CharacterSheetApiService(CharacterSheetRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
    }

    public CharacterSheetResponse getForAdventure(AdventureId requestedAdventureId, CharacterSheetId sheetId) {
        Objects.requireNonNull(requestedAdventureId, "requested adventure id must not be null");
        var sheet = repository.findById(Objects.requireNonNull(sheetId, "sheet id must not be null"))
                .orElseThrow(CharacterSheetNotFoundException::new);
        if (!sheet.adventureId().equals(requestedAdventureId)) {
            throw new CharacterSheetAdventureAccessDeniedException();
        }
        return CharacterSheetResponse.from(sheet);
    }
}
