package com.dndmaster.adventure.api;

import com.dndmaster.adventure.application.combat.AdventureCombatApplicationService;
import com.dndmaster.adventure.application.combat.CombatActionCommand;
import com.dndmaster.adventure.application.combat.CombatActorRole;
import com.dndmaster.adventure.application.guidance.AnswerRuleInquiryCommand;
import com.dndmaster.adventure.application.guidance.RuleGuidanceApplicationService;
import com.dndmaster.adventure.application.progress.AdventureProgressApplicationService;
import com.dndmaster.adventure.application.runtime.RuntimeTurnApplicationService;
import com.dndmaster.adventure.application.runtime.GmTurnRepository;
import com.dndmaster.adventure.application.runtime.RuntimeTurnResult;
import com.dndmaster.adventure.application.storyplan.AdventureStoryPlanApplicationService;
import com.dndmaster.adventure.application.runtime.SubmitRuntimeTurnCommand;
import com.dndmaster.adventure.application.saved.CreateAdventureCommand;
import com.dndmaster.adventure.application.saved.SavedAdventureApplicationService;
import com.dndmaster.adventure.application.scenario.AdventureScenarioApplicationService;
import com.dndmaster.adventure.application.scenario.ScenarioUpload;
import com.dndmaster.adventure.domain.adventure.*;
import com.dndmaster.adventure.domain.inquiry.InquiryId;
import com.dndmaster.adventure.domain.ruleset.DndEdition;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import com.dndmaster.adventure.domain.runtime.GmTurn;
import com.dndmaster.adventure.application.combat.CombatMapPort;
import com.fasterxml.jackson.databind.ObjectMapper;

@RestController
@RequestMapping
public class AdventureController {
    private static final String LEGACY_SCENARIO_UPLOAD_SUNSET = "Fri, 31 Dec 2027 00:00:00 GMT";
    private final SavedAdventureApplicationService savedAdventureService;
    private final RuntimeTurnApplicationService runtimeTurnService;
    private final com.dndmaster.adventure.application.saved.AdventureRepository adventureRepository;
    private final com.dndmaster.adventure.application.runtime.GmTurnFailureRecorder gmTurnFailureRecorder;
    private final GmTurnRepository gmTurnRepository;
    private final com.dndmaster.adventure.application.runtime.RuntimeTurnRepository runtimeTurnRepository;
    private final com.dndmaster.adventure.application.runtime.SessionEventRepository sessionEventRepository;
    private final RuleGuidanceApplicationService guidanceService;
    private final AdventureCombatApplicationService combatService;
    private final AdventureScenarioApplicationService scenarioService;
    private final AuthenticatedPlayerResolver playerResolver;
    private final CombatMapPort combatMapPort;
    private final com.dndmaster.adventure.application.combat.CombatMapViewPort combatMapViewPort;
    private final ObjectMapper objectMapper;
    private final AdventureStoryPlanApplicationService storyPlanService;

    public AdventureController(
            SavedAdventureApplicationService savedAdventureService,
            RuntimeTurnApplicationService runtimeTurnService,
            com.dndmaster.adventure.application.saved.AdventureRepository adventureRepository,
            com.dndmaster.adventure.application.runtime.GmTurnFailureRecorder gmTurnFailureRecorder,
            GmTurnRepository gmTurnRepository,
            com.dndmaster.adventure.application.runtime.RuntimeTurnRepository runtimeTurnRepository,
            com.dndmaster.adventure.application.runtime.SessionEventRepository sessionEventRepository,
            RuleGuidanceApplicationService guidanceService,
            AdventureCombatApplicationService combatService,
            AdventureScenarioApplicationService scenarioService,
            AuthenticatedPlayerResolver playerResolver,
            ObjectProvider<CombatMapPort> combatMapPort,
            ObjectMapper objectMapper,
            ObjectProvider<com.dndmaster.adventure.application.combat.CombatMapViewPort> combatMapViewPort,
            AdventureStoryPlanApplicationService storyPlanService) {
        this.savedAdventureService = savedAdventureService;
        this.runtimeTurnService = runtimeTurnService;
        this.adventureRepository = adventureRepository;
        this.gmTurnFailureRecorder = gmTurnFailureRecorder;
        this.gmTurnRepository = gmTurnRepository;
        this.runtimeTurnRepository = runtimeTurnRepository;
        this.sessionEventRepository = sessionEventRepository;
        this.guidanceService = guidanceService;
        this.combatService = combatService;
        this.storyPlanService = storyPlanService;
        this.scenarioService = scenarioService;
        this.playerResolver = playerResolver;
        this.combatMapPort = combatMapPort.getIfAvailable(() -> command -> {
            throw new IllegalStateException("combat map gateway unavailable");
        });
        this.combatMapViewPort = combatMapViewPort.getIfAvailable(() -> (adventureId1, ownerId) -> java.util.Optional.empty());
        this.objectMapper = objectMapper;
    }

    @PostMapping("/api/v1/adventures/scenarios")
    @Deprecated(forRemoval = false)
    @Operation(
            deprecated = true,
            summary = "Legacy one-file scenario upload",
            description = "Use bundle and package migration flows instead of the legacy one-file upload.")
    ResponseEntity<Void> uploadScenario(
            @RequestParam("file") MultipartFile file) throws Exception {
        UUID ownerId = playerResolver.playerId();
        ScenarioUpload upload = new ScenarioUpload(
                new com.dndmaster.adventure.domain.scenario.OwnerPlayerId(ownerId), file.getOriginalFilename(), file.getBytes());
        var scenario = scenarioService.uploadScenario(upload);
        return ResponseEntity.accepted()
                .header("Deprecation", "true")
                .header("Warning", "299 dnd-master \"Legacy one-file scenario upload is deprecated; migrate to bundle/package flows\"")
                .header("Sunset", LEGACY_SCENARIO_UPLOAD_SUNSET)
                .header("Link", "</api/v1/adventures/scenario-bundles>; rel=\"alternate\"")
                .header("X-Legacy-Scenario-Id", scenario.id().value().toString())
                .build();
    }

    @PostMapping("/api/v1/adventures/{adventureId}/messages")
    RuntimeTurnResponse streamAdventure(
            @PathVariable UUID adventureId, @RequestBody StreamMessageRequest request) {
        // 플레이어 입력을 런타임 턴으로 바꾸고, 서버가 만든 narration을 돌려준다.
        RuntimeTurnResult result = runtimeTurnService.submitTurn(new SubmitRuntimeTurnCommand(
                new AdventureId(adventureId),
                new OwnerPlayerId(playerResolver.playerId()),
                request.turnId(),
                request.commandId(),
                request.action()));
        return RuntimeTurnResponse.from(result);
    }

    /**
     * Lets the GM advance the scene without fabricating a player message. This is
     * used immediately after session start and whenever the UI asks the GM to
     * continue the current beat.
     */
    @PostMapping("/api/v1/adventures/{adventureId}/gm-turns")
    RuntimeTurnResponse continueGmTurn(
            @PathVariable UUID adventureId,
            @RequestBody(required = false) GmContinuationRequest request) {
        UUID owner = playerResolver.playerId();
        GmContinuationRequest input = request == null ? new GmContinuationRequest(null, null, null, null) : request;
        String action = input.instruction() == null || input.instruction().isBlank()
                ? "Continue the current adventure beat, reveal the next meaningful consequence, and end with a clear player-facing choice."
                : input.instruction();
        RuntimeTurnResult result = runtimeTurnService.submitTurn(new SubmitRuntimeTurnCommand(
                new AdventureId(adventureId), new OwnerPlayerId(owner),
                input.turnId() == null ? UUID.randomUUID() : input.turnId(),
                input.commandId() == null ? UUID.randomUUID() : input.commandId(),
                action, input.expectedVersion() == null ? -1 : input.expectedVersion(), null, -1, true, true, false));
        return RuntimeTurnResponse.from(result);
    }

    @PostMapping("/api/v1/adventures/{adventureId}/turns")
    public ResponseEntity<RuntimeTurnResponse> submitTypedTurn(
            @PathVariable UUID adventureId,
            @RequestHeader("Idempotency-Key") UUID commandId,
            @RequestHeader("If-Match-Version") long expectedVersion,
            @RequestBody GmTurnRequest request) {
        UUID owner = playerResolver.playerId();
        gmTurnRepository.lockAdventure(adventureId);
        var adventure = adventureRepository.findById(new AdventureId(adventureId)).orElseThrow();
        adventure.reopen(new OwnerPlayerId(owner));
        var input = request.input().toDomain();
        var existing = gmTurnRepository.findByCommandId(commandId);
        if (existing.isPresent()) {
            existing.get().assertSameCommand(input);
            var prior = runtimeTurnRepository.findByCommandId(commandId).orElseThrow(
                    () -> new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT, "turn is still processing"));
            return ResponseEntity.accepted().body(RuntimeTurnResponse.from(new RuntimeTurnResult(
                    prior, prior.context(), prior.conversation(), prior.version())));
        }
        GmTurn turn = GmTurn.start(request.turnId(), commandId, expectedVersion, input);
        gmTurnRepository.save(turn, adventureId);
        RuntimeTurnResult result;
        try {
            gmTurnRepository.save(turn.process(), adventureId);
            if (input instanceof com.dndmaster.adventure.domain.runtime.GmInput.MapActionInput mapAction) {
                applyMapAction(adventure, owner, commandId, mapAction);
            }
            result = runtimeTurnService.submitTurn(new SubmitRuntimeTurnCommand(
                    new AdventureId(adventureId), new OwnerPlayerId(owner), request.turnId(), commandId,
                    input.actionText(), expectedVersion,
                    null, -1, !(input instanceof com.dndmaster.adventure.domain.runtime.GmInput.MetaQuestionInput), true, false));
        } catch (RuntimeException exception) {
            gmTurnFailureRecorder.record(turn, adventureId, adventure.sessionId().value(), exception.getMessage(), expectedVersion);
            return ResponseEntity.status(org.springframework.http.HttpStatus.BAD_GATEWAY).build();
        }
        String providerMetadata = "provider=" + result.turn().plan().provider()
                + ";model=" + result.turn().plan().model()
                + ";reasoning=" + result.turn().plan().reasoning()
                + ";validation=accepted";
        gmTurnRepository.save(turn.process().commit(providerMetadata), adventureId);
        com.dndmaster.adventure.application.runtime.GmTurnCommitPolicy.requirePublishable(turn.process().commit(providerMetadata), result.version());
        sessionEventRepository.append(new com.dndmaster.adventure.domain.runtime.event.SessionEvent(
                result.turn().sessionId(), UUID.randomUUID(), result.version(), "GM_TURN_COMMITTED", result.turn().turnId().toString()));
        return ResponseEntity.accepted().body(RuntimeTurnResponse.from(result));
    }

    @PostMapping("/api/v1/adventures/{adventureId}/rule-inquiries")
    RuleInquiryResponse answerRuleInquiry(
            @PathVariable UUID adventureId, @RequestBody RuleInquiryRequest request) {
        AnswerRuleInquiryCommand command = new AnswerRuleInquiryCommand(
                new InquiryId(request.inquiryId()),
                new AdventureId(adventureId),
                new RuleSetId(request.ruleSetId()),
                new OwnerPlayerId(request.playerId()),
                request.situation());
        guidanceService.answerInquiry(command);
        return new RuleInquiryResponse(request.inquiryId(), "answered");
    }

    @GetMapping("/api/v1/adventures/{adventureId}/combat-map")
    CombatMapResponse playerMap(@PathVariable UUID adventureId) {
        var adventure = adventureRepository.findById(new AdventureId(adventureId)).orElseThrow();
        if (!adventure.ownerPlayerId().value().equals(playerResolver.playerId())) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN);
        }
        // A combat map is visible only while the current story stage declares one.
        // An old/preview activation must not leak into a town or event stage.
        var plan = storyPlanService.read(adventure.sessionId(), new OwnerPlayerId(playerResolver.playerId()));
        var currentStage = plan.stages().stream()
                .filter(stage -> stage.position() == plan.currentStage() + 1)
                .findFirst().orElse(null);
        if (currentStage == null || currentStage.mapDefinitionId() == null) {
            return new CombatMapResponse(adventureId, "stage-without-map", adventure.version(), null, null,
                    List.of(), List.of(), List.of(), List.of(), List.of(), null);
        }
        var projection = combatMapViewPort.playerView(adventureId, playerResolver.playerId());
        return projection.map(view -> CombatMapResponse.from(adventureId, adventure.version(), view))
                .orElseGet(() -> new CombatMapResponse(adventureId, "map-view", adventure.version(), null, null, List.of(), List.of(), List.of(), List.of(), List.of(), null));
    }

    @PostMapping("/api/v1/adventures/{adventureId}/dice-rolls")
    DiceRollResponse diceRoll(
            @PathVariable UUID adventureId, @RequestBody DiceRollRequest request) {
        CombatActionCommand command = new CombatActionCommand(
                UUID.randomUUID(),
                new AdventureId(adventureId),
                new RuleSetId(request.ruleSetId()),
                new CharacterSheetId(request.characterSheetId()),
                request.combatMapId(),
                CombatActorRole.valueOf(request.role()),
                request.action(),
                null,
                request.ownerPlayerId(),
                request.tokenId(),
                request.expectedVersion());
        var result = combatService.resolveCombatAction(command);
        return new DiceRollResponse(result.operationId(), result.role().name(), List.of(result.diceTotal()), result.diceTotal());
    }

    @PutMapping("/api/v1/adventures/{adventureId}/save")
    SaveAdventureResponse saveAdventure(
            @PathVariable UUID adventureId, @RequestBody SaveAdventureRequest request) {
        savedAdventureService.preserveProgress(
                new AdventureId(adventureId),
                new OwnerPlayerId(request.playerId()),
                request.expectedVersion(),
                new AdventureContext(request.currentScene(), null, null, null),
                List.of());
        return new SaveAdventureResponse(adventureId, request.expectedVersion() + 1);
    }

    @PostMapping("/api/v1/adventures/{adventureId}/resume")
    ResponseEntity<Void> resumeAdventure(@PathVariable UUID adventureId) {
        savedAdventureService.reopenAdventure(
                new AdventureId(adventureId),
                new OwnerPlayerId(playerResolver.playerId()));
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/api/v1/adventures/{adventureId}")
    ResponseEntity<Void> deleteAdventure(
            @PathVariable UUID adventureId, @RequestBody DeleteAdventureRequest request) {
        savedAdventureService.deleteAdventure(
                new AdventureId(adventureId),
                new OwnerPlayerId(request.playerId()),
                request.expectedVersion());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/internal/v1/adventures")
    List<AdventureSummaryResponse> ownedAdventures(@RequestParam UUID ownerId) {
        return savedAdventureService.listSavedAdventures(new OwnerPlayerId(ownerId)).stream()
                .map(a -> new AdventureSummaryResponse(a.id().value(), a.status().name(), a.version()))
                .toList();
    }

    @GetMapping("/internal/v1/adventures/{adventureId}/edition")
    EditionResponse appliedEdition(@PathVariable UUID adventureId) {
        return new EditionResponse(adventureId, "DND_5E_2024");
    }

    @GetMapping("/internal/v1/adventures/{adventureId}/roll-conditions")
    RollConditionsResponse rollConditions(@PathVariable UUID adventureId) {
        return new RollConditionsResponse(adventureId, "standard");
    }

    @PostMapping("/internal/v1/adventures/{adventureId}/movement-validations")
    MovementValidationResponse validateMovement(
            @PathVariable UUID adventureId, @RequestBody MovementValidationRequest request) {
        return new MovementValidationResponse(adventureId, true, "valid");
    }

    @GetMapping("/internal/v1/adventures/{adventureId}/gm-context")
    GmContextResponse gmContext(@PathVariable UUID adventureId) {
        return new GmContextResponse(adventureId, "current-scene", "npc-state");
    }

    public record StreamMessageRequest(UUID playerId, UUID turnId, UUID commandId, String action) {}

    public record GmContinuationRequest(UUID turnId, UUID commandId, Long expectedVersion, String instruction) {}

    public record GmTurnRequest(UUID turnId, GmInputRequest input) {}

    public record GmInputRequest(String type, String text, UUID mapId, Long mapVersion, String action, String question) {
        com.dndmaster.adventure.domain.runtime.GmInput toDomain() {
            if (type == null) throw new IllegalArgumentException("input type is required");
            return switch (type) {
                case "TEXT" -> new com.dndmaster.adventure.domain.runtime.GmInput.TextInput(text);
                case "MAP_ACTION" -> new com.dndmaster.adventure.domain.runtime.GmInput.MapActionInput(mapId, mapVersion == null ? -1 : mapVersion, action);
                case "META_QUESTION" -> new com.dndmaster.adventure.domain.runtime.GmInput.MetaQuestionInput(question);
                default -> throw new IllegalArgumentException("unsupported input type: " + type);
            };
        }
        String actionText() { return toDomain().actionText(); }
    }
    // 프런트가 바로 보여줄 수 있게 턴 결과를 압축한 응답이다.
    public record RuntimeTurnResponse(
            UUID turnId,
            UUID adventureId,
            UUID scenarioPackageId,
            long bindingVersion,
            String narration,
            String judgment,
            String currentScene,
            List<String> sourceRefs,
            List<String> warnings,
            String provider,
            String model,
            String reasoning,
            long version) {
        static RuntimeTurnResponse from(RuntimeTurnResult result) {
            return new RuntimeTurnResponse(
                    result.turn().turnId(),
                    result.turn().adventureId().value(),
                    result.turn().scenarioPackageId(),
                    result.turn().bindingVersion(),
                    result.turn().plan().narration(),
                    result.turn().plan().judgment(),
                    result.context().currentScene(),
                    result.turn().citations(),
                    result.turn().warnings(),
                    result.turn().plan().provider(),
                    result.turn().plan().model(),
                    result.turn().plan().reasoning(),
                    result.version());
        }
    }
    public record RuleInquiryRequest(UUID inquiryId, UUID ruleSetId, UUID playerId, String situation) {}
    public record RuleInquiryResponse(UUID inquiryId, String status) {}
    private void applyMapAction(Adventure adventure, UUID owner, UUID commandId,
            com.dndmaster.adventure.domain.runtime.GmInput.MapActionInput input) {
        try {
            MapActionPayload payload = objectMapper.readValue(input.action(), MapActionPayload.class);
            if (!input.mapId().equals(payload.mapId()) || input.mapVersion() != payload.mapVersion()) {
                throw new IllegalArgumentException("map action identity mismatch");
            }
            if (payload.action() == null || payload.action().isBlank()) {
                throw new IllegalArgumentException("map action type required");
            }
            if (!"MOVE".equals(payload.action())) {
                return;
            }
            var member = adventure.party().stream().findFirst()
                    .orElseThrow(() -> new IllegalStateException("map action requires a party member"));
            String path = payload.path() == null ? null : payload.path().stream()
                    .map(position -> position.x() + "," + position.y()).reduce((left, right) -> left + ";" + right).orElse(null);
            combatMapPort.validateAndMove(new CombatActionCommand(
                    commandId, adventure.id(), adventure.ruleSetId(), member.characterSheetId(), payload.mapId(),
                    CombatActorRole.PLAYER, payload.action(), path, owner, payload.tokenId(), payload.mapVersion()));
        } catch (java.io.IOException exception) {
            throw new IllegalArgumentException("invalid map action", exception);
        }
    }

    public record MapActionPayload(UUID mapId, long mapVersion, UUID tokenId, String action,
            List<PositionPayload> path, UUID targetId, PositionPayload location) {}
    public record PositionPayload(int x, int y) {}
    public record CombatMapResponse(UUID adventureId, String status, long sessionVersion, UUID mapId,
            com.dndmaster.adventure.application.combat.CombatMapViewPort.Grid grid,
            List<com.dndmaster.adventure.application.combat.CombatMapViewPort.Token> tokens,
            List<com.dndmaster.adventure.application.combat.CombatMapViewPort.Obstacle> obstacles,
            List<com.dndmaster.adventure.application.combat.CombatMapViewPort.Layer> layers,
            List<com.dndmaster.adventure.application.combat.CombatMapViewPort.Position> current,
            List<com.dndmaster.adventure.application.combat.CombatMapViewPort.Position> explored, Long version) {
        static CombatMapResponse from(UUID adventureId, long sessionVersion, com.dndmaster.adventure.application.combat.CombatMapViewPort.View view) {
            return new CombatMapResponse(adventureId, "authoritative-map", sessionVersion, view.mapId(), view.grid(), view.tokens(), view.obstacles(), view.layers(), view.current(), view.explored(), view.version());
        }
    }
    public record DiceRollRequest(
            UUID ruleSetId,
            UUID characterSheetId,
            UUID combatMapId,
            UUID ownerPlayerId,
            UUID tokenId,
            long expectedVersion,
            String role,
            String action) {}
    public record DiceRollResponse(UUID rollId, String scope, List<Integer> faces, int total) {}
    public record SaveAdventureRequest(UUID playerId, long expectedVersion, String currentScene) {}
    public record SaveAdventureResponse(UUID adventureId, long newVersion) {}
    public record DeleteAdventureRequest(UUID playerId, long expectedVersion) {}
    public record AdventureSummaryResponse(UUID adventureId, String status, long version) {}
    public record EditionResponse(UUID adventureId, String edition) {}
    public record RollConditionsResponse(UUID adventureId, String conditions) {}
    public record MovementValidationRequest(UUID tokenId, int x, int y) {}
    public record MovementValidationResponse(UUID adventureId, boolean valid, String reason) {}
    public record GmContextResponse(UUID adventureId, String currentScene, String npcState) {}
}
