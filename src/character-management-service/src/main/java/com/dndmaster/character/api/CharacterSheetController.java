package com.dndmaster.character.api;

import com.dndmaster.character.application.CharacterSheetApplicationService;
import com.dndmaster.character.application.CreateCharacterSheetCommand;
import com.dndmaster.character.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.dndmaster.character.application.CharacterSheetsDeletionConsumer;
import com.dndmaster.character.application.CharacterSheetsDeletionRequested;

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

    @GetMapping("/internal/v1/character-rules/catalogs/{edition}")
    CharacterRulesCatalogResponse getCharacterRulesCatalog(@PathVariable String edition) {
        if (!"DND_5E_2014".equals(edition)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "CHARACTER_RULES_CATALOG_NOT_FOUND");
        }
        return new CharacterRulesCatalogResponse(
                edition,
                "DND_5E_2014",
                1,
                List.of("드워프", "엘프", "하플링", "인간"),
                List.of("바바리안", "바드", "클레릭", "드루이드", "파이터", "몽크", "팔라딘", "레인저", "로그", "소서러", "워락", "위저드"),
                List.of("수행사제", "사기꾼", "범죄자", "연예인", "민중 영웅", "길드 장인", "은둔자", "귀족", "이방인", "현자", "선원", "군인", "부랑아"));
    }

    @PostMapping("/internal/v1/adventure-sessions/{sessionId}/character-builds/evaluate")
    Dnd5e2014CharacterBuildEvaluator.Evaluation evaluateCharacterBuild(
            @PathVariable UUID sessionId,
            @RequestBody CharacterSheetRequest request) {
        if (!"DND_5E_2014".equals(request.edition())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "UNSUPPORTED_CHARACTER_BUILD_EVALUATION_EDITION");
        }
        return Dnd5e2014CharacterBuildEvaluator.evaluate(request);
    }

    @PostMapping("/internal/v1/adventure-sessions/{sessionId}/character-sheets")
    CharacterSheetResponse createCharacterSheet(@PathVariable UUID sessionId, @RequestBody CharacterSheetRequest request) {
        Dnd5e2014CharacterCreationValidator.validateCreation(request);
        Dnd5e2014CharacterBuildEvaluator.Evaluation evaluation = "DND_5E_2014".equals(request.edition())
                ? Dnd5e2014CharacterBuildEvaluator.evaluate(request)
                : null;
        if (evaluation != null && !evaluation.valid()) {
            throw new CharacterMutationRejectedException(evaluation.violations());
        }
        String derivedStatistics = evaluation == null ? request.derivedStatistics() : evaluation.serializedDerived();
        CharacterSheet sheet = characterSheetService.createSheet(new CreateCharacterSheetCommand(
                new SessionId(sessionId),
                request.ownerPlayerId(),
                SheetEdition.valueOf(request.edition()),
                parseData(request.edition(), request.characterName(), request.level(), request.inspiration(), request.race(), request.characterClass(), request.background(), startingAbilities(request), derivedStatistics, request.characterBuild(), request.characterState())));
        return CharacterSheetResponse.from(sheet);
    }

    @GetMapping("/internal/v1/character-sheets/{sheetId}")
    CharacterSheetResponse getCharacterSheet(
            @PathVariable UUID sheetId,
            @RequestParam(defaultValue = "DND_5E_2024") String edition) {
        CharacterSheet sheet = characterSheetService.openSheet(
                new CharacterSheetId(sheetId), SheetEdition.valueOf(edition));
        return CharacterSheetResponse.from(sheet);
    }

    @GetMapping("/internal/v1/character-sheets")
    List<CharacterSheetSummaryResponse> listCharacterSheets(@RequestParam UUID ownerPlayerId) {
        return characterSheetService.listSheetsOwnedBy(ownerPlayerId).stream().map(CharacterSheetSummaryResponse::from).toList();
    }

    @PostMapping("/internal/v1/adventure-sessions/{sessionId}/character-sheets/{sheetId}/copy")
    CharacterSheetResponse copyCharacterSheet(@PathVariable UUID sessionId, @PathVariable UUID sheetId, @RequestBody CopyCharacterSheetRequest request) {
        return CharacterSheetResponse.from(characterSheetService.copyOwnedSheet(new CharacterSheetId(sheetId), new SessionId(sessionId), request.ownerPlayerId()));
    }

    @GetMapping("/internal/v1/adventure-sessions/{sessionId}/character-sheets/{sheetId}/ownership")
    CharacterSheetResponse verifyOwnership(@RequestHeader(value = "X-Internal-Token", required = false) String token, @RequestHeader("X-Owner-Player-ID") UUID ownerPlayerId, @PathVariable UUID sessionId, @PathVariable UUID sheetId) {
        if (requestGuard == null) throw new IllegalStateException("request guard is not configured");
        requestGuard.internal(token);
        return CharacterSheetResponse.from(characterSheetService.verifySessionOwnership(new CharacterSheetId(sheetId), new SessionId(sessionId), ownerPlayerId));
    }

    @PutMapping("/internal/v1/character-sheets/{sheetId}")
    CharacterSheetResponse preserveCharacterSheet(
            @PathVariable UUID sheetId,
            @RequestHeader("Idempotency-Key") UUID commandId,
            @RequestHeader("If-Match-Version") long expectedVersion,
            @RequestBody CharacterSheetRequest request) {
        String derivedStatistics = request.derivedStatistics();
        if ("DND_5E_2014".equals(request.edition()) && structuredPayload(request)) {
            Dnd5e2014CharacterBuildEvaluator.Evaluation evaluation = Dnd5e2014CharacterBuildEvaluator.evaluate(request);
            if (!evaluation.valid()) throw new CharacterMutationRejectedException(evaluation.violations());
            derivedStatistics = evaluation.serializedDerived();
        }
        CharacterSheetUpdate update = new CharacterSheetUpdate(
                SheetEdition.valueOf(request.edition()),
                parseData(request.edition(), request.characterName(), request.level(), request.inspiration(), request.race(), request.characterClass(), request.background(), startingAbilities(request), derivedStatistics, request.characterBuild(), request.characterState()),
                InputMode.STRUCTURED_SHEET,
                commandId,
                expectedVersion);
        CharacterSheet sheet = characterSheetService.manageCharacter(new CharacterSheetId(sheetId), update);
        return CharacterSheetResponse.from(sheet);
    }

    private static boolean structuredPayload(CharacterSheetRequest request) {
        return request.characterBuild() != null && !request.characterBuild().isBlank()
                || request.characterState() != null && !request.characterState().isBlank();
    }

    private static CharacterSheetData parseData(
            String edition, String characterName, int level, boolean inspiration, String race, String characterClass, String background, String startingAbilities, String derivedStatistics, String characterBuild, String characterState) {
        return switch (SheetEdition.valueOf(edition)) {
            case DND_5E_2014 -> new CharacterSheetData2014(characterName, level, inspiration, race, characterClass, background, startingAbilities, derivedStatistics, characterBuild, characterState);
            case DND_5E_2024 -> new CharacterSheetData2024(characterName, level, inspiration, race, characterClass, background, startingAbilities, derivedStatistics, characterBuild, characterState);
        };
    }

    private static String startingAbilities(CharacterSheetRequest request) {
        if (request.startingAbilities() != null && !request.startingAbilities().isBlank()) return request.startingAbilities();
        if (request.blueprintValues() == null) return request.startingAbilities();
        return request.blueprintValues().entrySet().stream()
                .filter(entry -> entry.getKey().startsWith("starting_ability_scores.") && entry.getValue() != null && !entry.getValue().isBlank())
                .map(entry -> entry.getKey().substring("starting_ability_scores.".length()) + "=" + entry.getValue())
                .collect(java.util.stream.Collectors.joining(","));
    }

    public record CharacterSheetRequest(
            UUID adventureId, UUID ownerPlayerId, String edition, String characterName, int level, boolean inspiration,
            String race, String characterClass, String background, String startingAbilities, String derivedStatistics, String characterBuild, String characterState,
            Map<String, String> blueprintValues) {
        public CharacterSheetRequest(UUID adventureId, UUID ownerPlayerId, String edition, String characterName,
                                     int level, boolean inspiration, String race, String characterClass,
                                     String background, String startingAbilities) {
            this(adventureId, ownerPlayerId, edition, characterName, level, inspiration, race, characterClass,
                    background, startingAbilities, null, null, null, null);
        }
    }

    public record CharacterRulesCatalogResponse(
            String edition,
            String baseSchema,
            int revision,
            List<String> races,
            List<String> classes,
            List<String> backgrounds) {}

    public record CharacterSheetsDeletionRequest(UUID sessionId, java.util.List<UUID> characterSheetIds) {}
    public record CopyCharacterSheetRequest(UUID ownerPlayerId) {}
    public record CharacterSheetSummaryResponse(UUID characterSheetId, String characterName, int level, String race, String characterClass, String background) {
        static CharacterSheetSummaryResponse from(CharacterSheet sheet) {
            return new CharacterSheetSummaryResponse(sheet.id().value(), sheet.data().characterName(), sheet.data().level(), sheet.data().race(), sheet.data().characterClass(), sheet.data().background());
        }
    }
}
