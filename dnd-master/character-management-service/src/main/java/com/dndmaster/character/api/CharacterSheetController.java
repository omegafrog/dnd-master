package com.dndmaster.character.api;

import com.dndmaster.character.application.CharacterSheetApplicationService;
import com.dndmaster.character.domain.*;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping
public class CharacterSheetController {
    private final CharacterSheetApplicationService characterSheetService;

    public CharacterSheetController(CharacterSheetApplicationService characterSheetService) {
        this.characterSheetService = characterSheetService;
    }

    @GetMapping("/internal/v1/character-sheets/{sheetId}")
    CharacterSheetResponse getCharacterSheet(@PathVariable UUID sheetId) {
        CharacterSheet sheet = characterSheetService.openSheet(
                new CharacterSheetId(sheetId), SheetEdition.DND_5E_2024);
        return CharacterSheetResponse.from(sheet);
    }

    @PutMapping("/internal/v1/character-sheets/{sheetId}")
    CharacterSheetResponse preserveCharacterSheet(
            @PathVariable UUID sheetId, @RequestBody CharacterSheetRequest request) {
        CharacterSheetUpdate update = new CharacterSheetUpdate(
                SheetEdition.valueOf(request.edition()),
                parseData(request.edition(), request.characterName(), request.level(), request.inspiration()),
                InputMode.STRUCTURED_SHEET);
        CharacterSheet sheet = characterSheetService.manageCharacter(new CharacterSheetId(sheetId), update);
        return CharacterSheetResponse.from(sheet);
    }

    private static CharacterSheetData parseData(
            String edition, String characterName, int level, boolean inspiration) {
        return switch (SheetEdition.valueOf(edition)) {
            case DND_5E_2014 -> new CharacterSheetData2014(characterName, level, inspiration);
            case DND_5E_2024 -> new CharacterSheetData2024(characterName, level, inspiration);
        };
    }

    public record CharacterSheetRequest(
            String edition, String characterName, int level, boolean inspiration) {}
}
