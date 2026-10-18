package com.dndmaster.adventure.api;

import com.dndmaster.adventure.application.ability.AdventureAbilityDefinitionPreparationService;
import com.dndmaster.adventure.application.ability.AdventureAbilityDefinitionRepository;
import com.dndmaster.adventure.application.runtime.RuntimeCharacterSheetReadPort;
import com.dndmaster.adventure.application.saved.AdventureRepository;
import com.dndmaster.adventure.domain.adventure.AdventureId;
import com.dndmaster.adventure.domain.adventure.OwnerPlayerId;
import com.dndmaster.adventure.domain.adventure.SessionId;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public final class AdventureAbilityDefinitionController {
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(AdventureAbilityDefinitionController.class);
    private final AdventureRepository adventures;
    private final RuntimeCharacterSheetReadPort sheets;
    private final AdventureAbilityDefinitionPreparationService preparation;
    private final AdventureAbilityDefinitionRepository definitions;
    private final ObjectMapper mapper;
    private final ApiRequestGuard guard;

    public AdventureAbilityDefinitionController(AdventureRepository adventures, RuntimeCharacterSheetReadPort sheets,
            AdventureAbilityDefinitionPreparationService preparation, AdventureAbilityDefinitionRepository definitions,
            ObjectMapper mapper, @Value("${adventure.integration.internal-token:${INTERNAL_SERVICE_TOKEN:}}") String token) {
        this.adventures = adventures; this.sheets = sheets; this.preparation = preparation;
        this.definitions = definitions; this.mapper = mapper; this.guard = new ApiRequestGuard(token);
    }

    @PostMapping("/internal/v1/adventures/abilities/prepare")
    void prepare(@RequestHeader(value = "X-Internal-Token", required = false) String token, @RequestBody Request request) {
        guard.internal(token);
        var adventure = requireScope(request);
        try {
            preparation.prepare(new AdventureId(adventure.id().value()), new SessionId(request.sessionId()),
                    new OwnerPlayerId(request.ownerId()), request.abilityIds(), request.sourceDefinition());
        } catch (com.dndmaster.adventure.application.ability.AbilityDefinitionSourceInputRequiredException required) {
            throw new ApiRequestGuard.ApiContractException(409, "ABILITY_SOURCE_INPUT_REQUIRED:" + required.abilityId());
        }
    }

    @PostMapping("/internal/v1/adventures/abilities/activate")
    void activate(@RequestHeader(value = "X-Internal-Token", required = false) String token, @RequestBody Request request) {
        guard.internal(token);
        var adventure = requireScope(request);
        JsonNode sheet = readSheet(request.characterSheetId());
        List<String> learned = learnedIds(sheet);
        if (!learned.containsAll(request.abilityIds())) throw new ApiRequestGuard.ApiContractException(409, "ABILITY_NOT_REGISTERED_ON_SHEET");
        preparation.activate(adventure.id().value(), request.abilityIds());
    }

    @PostMapping("/internal/v1/adventures/abilities/lookup")
    LookupResponse lookup(@RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestBody LookupRequest request) {
        guard.internal(token);
        long startedAt = System.nanoTime();
        logLookup("started", request, "active_definition_for_learned_ability", "lookup_started", "none", startedAt);
        try {
            return lookupScoped(request, startedAt);
        } catch (RuntimeException | Error failure) {
            logLookup("failed", request, "active_definition_for_learned_ability",
                    failure.getClass().getSimpleName(), failure.getClass().getSimpleName(), startedAt);
            throw failure;
        }
    }

    private LookupResponse lookupScoped(LookupRequest request, long startedAt) {
        if (request == null || request.adventureId() == null || request.sessionId() == null || request.ownerId() == null
                || request.characterSheetId() == null || request.abilityId() == null || request.abilityId().isBlank())
            throw new ApiRequestGuard.ApiContractException(400, "INVALID_ABILITY_LOOKUP_SCOPE");
        var adventure = findAdventure(request.sessionId(), request.ownerId());
        if (!request.adventureId().equals(adventure.id().value()) && !request.adventureId().equals(request.sessionId()))
            throw new ApiRequestGuard.ApiContractException(404, "ADVENTURE_NOT_FOUND");
        JsonNode sheet = readSheet(request.characterSheetId());
        validateSheetScope(sheet, request.sessionId(), request.ownerId());
        if (!learnedIds(sheet).contains(request.abilityId())) {
            logLookup("completed", request, "learned_ability", "NOT_LEARNED", "none", startedAt);
            return new LookupResponse("NOT_LEARNED", null);
        }
        var stored = definitions.find(adventure.id().value(), request.abilityId());
        if (stored.isEmpty()) {
            logLookup("completed", request, "active_definition", "NEEDS_PREPARATION", "none", startedAt);
            return new LookupResponse("NEEDS_PREPARATION", null);
        }
        if (!"ACTIVE".equals(stored.get().status())) {
            logLookup("completed", request, "active_definition", "NEEDS_PREPARATION", "none", startedAt);
            return new LookupResponse("NEEDS_PREPARATION", null);
        }
        logLookup("completed", request, "active_definition", "FOUND", "none", startedAt);
        return new LookupResponse("FOUND", stored.get().definition());
    }

    private static void logLookup(String outcome, LookupRequest request, String expected, String actual,
            String failureKind, long startedAt) {
        if (!com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.enabled()) return;
        String adventureId = request == null ? "none" : String.valueOf(request.adventureId());
        String sessionId = request == null ? "none" : String.valueOf(request.sessionId());
        String ownerId = request == null ? "none" : String.valueOf(request.ownerId());
        String sheetId = request == null ? "none" : String.valueOf(request.characterSheetId());
        String abilityId = request == null ? "none" : String.valueOf(request.abilityId());
        LOGGER.info("dev_ability_lookup outcome={} adventureId={} sessionId={} ownerId={} characterSheetId={} abilityId={} expected={} actual={} failureKind={} elapsedMs={} recovery={}",
                outcome, adventureId, sessionId, ownerId, sheetId, abilityId, expected, actual, failureKind,
                java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt),
                "failed".equals(outcome) ? "return_scoped_error" : "none");
    }

    private com.dndmaster.adventure.domain.adventure.Adventure requireScope(Request request) {
        if (request == null || request.adventureId() == null || request.sessionId() == null || request.ownerId() == null
                || request.characterSheetId() == null || request.abilityIds() == null || request.abilityIds().isEmpty()
                || request.abilityIds().size() > 100 || request.abilityIds().stream().anyMatch(id -> id == null || id.isBlank()))
            throw new ApiRequestGuard.ApiContractException(400, "INVALID_ABILITY_PREPARATION_REQUEST");
        var adventure = findAdventure(request.sessionId(), request.ownerId());
        if (!request.adventureId().equals(adventure.id().value()) && !request.adventureId().equals(request.sessionId()))
            throw new ApiRequestGuard.ApiContractException(404, "ADVENTURE_NOT_FOUND");
        validateSheetScope(readSheet(request.characterSheetId()), request.sessionId(), request.ownerId());
        return adventure;
    }

    private com.dndmaster.adventure.domain.adventure.Adventure findAdventure(UUID sessionId, UUID ownerId) {
        return adventures.findBySessionId(new SessionId(sessionId))
                .filter(item -> item.ownerPlayerId().value().equals(ownerId))
                .orElseThrow(() -> new ApiRequestGuard.ApiContractException(404, "ADVENTURE_NOT_FOUND"));
    }

    private JsonNode readSheet(UUID sheetId) {
        try { return mapper.readTree(sheets.read(sheetId)); }
        catch (RuntimeException | java.io.IOException failure) {
            throw new ApiRequestGuard.ApiContractException(503, "CHARACTER_SHEET_LOOKUP_FAILED");
        }
    }

    private static void validateSheetScope(JsonNode sheet, UUID sessionId, UUID ownerId) {
        if (!sessionId.toString().equals(sheet.path("sessionId").asText())
                || !ownerId.toString().equals(sheet.path("ownerPlayerId").asText()))
            throw new ApiRequestGuard.ApiContractException(404, "CHARACTER_SHEET_NOT_IN_ADVENTURE");
    }

    private static List<String> learnedIds(JsonNode sheet) {
        try {
            JsonNode state = new ObjectMapper().readTree(sheet.path("characterState").asText("{}"));
            JsonNode ids = state.path("learnedAbilityIds");
            if (!ids.isArray()) throw new ApiRequestGuard.ApiContractException(503, "CHARACTER_SHEET_ABILITY_STATE_INVALID");
            return java.util.stream.StreamSupport.stream(ids.spliterator(), false).filter(JsonNode::isTextual)
                    .map(JsonNode::asText).toList();
        } catch (java.io.IOException failure) {
            throw new ApiRequestGuard.ApiContractException(503, "CHARACTER_SHEET_ABILITY_STATE_INVALID");
        }
    }

    record Request(UUID adventureId, UUID sessionId, UUID ownerId, UUID characterSheetId, List<String> abilityIds,
            JsonNode sourceDefinition) {}
    record LookupRequest(UUID adventureId, UUID sessionId, UUID ownerId, UUID characterSheetId, String abilityId) {}
    record LookupResponse(String status, JsonNode definition) {}
}
