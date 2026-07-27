package com.dndmaster.character.api;

import com.dndmaster.character.application.CharacterSheetApplicationService;
import com.dndmaster.character.application.CreateCharacterSheetCommand;
import com.dndmaster.character.domain.*;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;
import com.dndmaster.character.application.CharacterSheetsDeletionConsumer;
import com.dndmaster.character.application.CharacterSheetsDeletionRequested;
import com.dndmaster.character.api.ApiRequestGuard;

@RestController
@RequestMapping
public class CharacterSheetController {
    private final CharacterSheetApplicationService characterSheetService;
    private CharacterSheetsDeletionConsumer deletionConsumer;
    private ApiRequestGuard requestGuard;

    public CharacterSheetController(CharacterSheetApplicationService characterSheetService) {
        this.characterSheetService = characterSheetService;
    }

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    void setDeletionConsumer(CharacterSheetsDeletionConsumer deletionConsumer) { this.deletionConsumer = deletionConsumer; }
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    void setRequestGuard(ApiRequestGuard requestGuard) { this.requestGuard = requestGuard; }

    @PostMapping("/internal/v1/character-sheets/deletion-requests")
    void deleteCharacterSheets(@RequestHeader(value = "X-Internal-Token", required = false) String token, @RequestBody CharacterSheetsDeletionRequest request) {
        if (requestGuard == null) throw new IllegalStateException("request guard is not configured");
        requestGuard.internal(token);
        if (deletionConsumer == null) throw new IllegalStateException("deletion consumer is not configured");
        deletionConsumer.consume(new CharacterSheetsDeletionRequested(request.sessionId(), request.characterSheetIds()));
    }

    @PostMapping("/internal/v1/adventure-sessions/{sessionId}/character-sheets")
    CharacterSheetResponse createCharacterSheet(@PathVariable UUID sessionId, @RequestBody CharacterSheetRequest request) {
        CharacterSheet sheet = characterSheetService.createSheet(new CreateCharacterSheetCommand(
                new SessionId(sessionId),
                SheetEdition.valueOf(request.edition()),
                parseData(request.edition(), request.characterName(), request.level(), request.inspiration(), request.race(), request.characterClass(), request.background(), request.startingAbilities())));
        return CharacterSheetResponse.from(sheet);
    }

    @GetMapping("/internal/v1/character-sheets/{sheetId}")
    CharacterSheetResponse getCharacterSheet(@PathVariable UUID sheetId) {
        CharacterSheet sheet = characterSheetService.openSheet(
                new CharacterSheetId(sheetId), SheetEdition.DND_5E_2024);
        return CharacterSheetResponse.from(sheet);
    }

    @PutMapping("/internal/v1/character-sheets/{sheetId}")
    CharacterSheetResponse preserveCharacterSheet(
            @PathVariable UUID sheetId,
            @RequestHeader("Idempotency-Key") UUID commandId,
            @RequestHeader("If-Match-Version") long expectedVersion,
            @RequestBody CharacterSheetRequest request) {
        CharacterSheetUpdate update = new CharacterSheetUpdate(
                SheetEdition.valueOf(request.edition()),
                parseData(request.edition(), request.characterName(), request.level(), request.inspiration(), request.race(), request.characterClass(), request.background(), request.startingAbilities()),
                InputMode.STRUCTURED_SHEET,
                commandId,
                expectedVersion);
        CharacterSheet sheet = characterSheetService.manageCharacter(new CharacterSheetId(sheetId), update);
        return CharacterSheetResponse.from(sheet);
    }

    private static CharacterSheetData parseData(
            String edition, String characterName, int level, boolean inspiration, String race, String characterClass, String background, String startingAbilities) {
        return switch (SheetEdition.valueOf(edition)) {
            case DND_5E_2014 -> new CharacterSheetData2014(characterName, level, inspiration, race, characterClass, background, startingAbilities);
            case DND_5E_2024 -> new CharacterSheetData2024(characterName, level, inspiration, race, characterClass, background, startingAbilities);
        };
    }

    public record CharacterSheetRequest(
            UUID adventureId, String edition, String characterName, int level, boolean inspiration,
            String race, String characterClass, String background, String startingAbilities) {}
    public record CharacterSheetsDeletionRequest(UUID sessionId, java.util.List<UUID> characterSheetIds) {}
}
