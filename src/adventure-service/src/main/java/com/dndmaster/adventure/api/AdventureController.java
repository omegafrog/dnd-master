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
import com.dndmaster.adventure.application.runtime.SubmitRuntimeTurnCommand;
import com.dndmaster.adventure.application.runtime.RuntimeEvidenceOverride;
import com.dndmaster.adventure.application.runtime.EvidencePack;
import com.dndmaster.adventure.application.runtime.RuntimeEvidence;
import com.dndmaster.adventure.application.runtime.RuntimeEvidenceType;
import com.dndmaster.adventure.application.runtime.PlayerVisibleStoryEvidence;
import com.dndmaster.adventure.application.runtime.StoryEvidenceVisibility;
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
            ObjectProvider<com.dndmaster.adventure.application.combat.CombatMapViewPort> combatMapViewPort) {
        this.savedAdventureService = savedAdventureService;
        this.runtimeTurnService = runtimeTurnService;
        this.adventureRepository = adventureRepository;
        this.gmTurnFailureRecorder = gmTurnFailureRecorder;
        this.gmTurnRepository = gmTurnRepository;
        this.runtimeTurnRepository = runtimeTurnRepository;
        this.sessionEventRepository = sessionEventRepository;
        this.guidanceService = guidanceService;
        this.combatService = combatService;
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

    @PostMapping("/api/v1/adventures/{adventureId}/turns")
    public ResponseEntity<?> submitTypedTurn(
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
        GmTurn turn;
        if (existing.isPresent()) {
            existing.get().assertSameCommand(input);
            if (existing.get().status() == com.dndmaster.adventure.domain.runtime.GmTurnStatus.COMMITTED) {
                var prior = runtimeTurnRepository.findByCommandId(commandId).orElseThrow(
                        () -> new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT, "turn is still processing"));
                return ResponseEntity.accepted().body(RuntimeTurnResponse.from(new RuntimeTurnResult(
                        prior, prior.context(), prior.conversation(), prior.version())));
            }
            if (existing.get().status() == com.dndmaster.adventure.domain.runtime.GmTurnStatus.FAILED) {
                turn = existing.get().retry();
                gmTurnRepository.save(turn, adventureId);
            } else {
                throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT, "turn is still processing");
            }
        } else {
            turn = GmTurn.start(request.turnId(), commandId, expectedVersion, input);
            gmTurnRepository.save(turn, adventureId);
        }
        RuntimeTurnResult result;
        try {
            if (turn.status() == com.dndmaster.adventure.domain.runtime.GmTurnStatus.STARTED) {
                turn = turn.process();
                gmTurnRepository.save(turn, adventureId);
            }
            if (input instanceof com.dndmaster.adventure.domain.runtime.GmInput.MapActionInput mapAction) {
                applyMapAction(adventure, owner, commandId, mapAction);
            }
            SubmitRuntimeTurnCommand runtimeCommand = new SubmitRuntimeTurnCommand(
                    new AdventureId(adventureId), new OwnerPlayerId(owner), request.turnId(), commandId,
                    input.actionText(), expectedVersion,
                    !(input instanceof com.dndmaster.adventure.domain.runtime.GmInput.MetaQuestionInput));
            if (request.ragCondition() != null) {
                runtimeCommand = SubmitRuntimeTurnCommand.withEvidenceOverride(
                        new AdventureId(adventureId), new OwnerPlayerId(owner), request.turnId(), commandId,
                        input.actionText(), expectedVersion, request.ragOverride());
            }
            result = runtimeTurnService.submitTurn(runtimeCommand);
        } catch (RuntimeException exception) {
            String safeMessage = exception instanceof com.dndmaster.adventure.infrastructure.integration.GmAgentFailureException failure
                    ? failure.failure().safeMessage() : "게임 마스터를 일시적으로 사용할 수 없습니다. 다시 시도해 주세요.";
            gmTurnFailureRecorder.record(turn, adventureId, adventure.sessionId().value(), safeMessage, expectedVersion);
            var failure = exception instanceof com.dndmaster.adventure.infrastructure.integration.GmAgentFailureException typed
                    ? typed.failure() : new com.dndmaster.adventure.application.runtime.GmAgentFailure(
                            "DEPENDENCY", true, safeMessage, commandId.toString());
            return ResponseEntity.status(org.springframework.http.HttpStatus.BAD_GATEWAY).body(new GmTurnFailureResponse(
                    failure.category(), failure.retryable(), failure.safeMessage(), failure.correlationId(), expectedVersion));
        }
        String providerMetadata = "provider=" + result.turn().plan().provider()
                + ";model=" + result.turn().plan().model()
                + ";reasoning=" + result.turn().plan().reasoning()
                + ";validation=accepted";
        gmTurnRepository.save(turn.process().commit(providerMetadata), adventureId);
        com.dndmaster.adventure.application.runtime.GmTurnCommitPolicy.requirePublishable(turn.process().commit(providerMetadata), result.version());
        sessionEventRepository.append(new com.dndmaster.adventure.domain.runtime.event.SessionEvent(
                result.turn().sessionId(), UUID.randomUUID(), result.version(), "GM_TURN_COMMITTED", result.turn().turnId().toString()));
        return ResponseEntity.accepted().body(RuntimeTurnResponse.from(result, sessionEventRepository));
    }

    public record GmTurnFailureResponse(String category, boolean retryable, String safeMessage,
                                        String correlationId, long version) {}

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
        var projection = combatMapViewPort.playerView(adventureId, playerResolver.playerId());
        return projection.map(view -> CombatMapResponse.from(adventureId, adventure.version(), view))
                .orElseGet(() -> new CombatMapResponse(adventureId, "map-view", adventure.version(), null, null, List.of(), List.of(), List.of(), null));
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

    public record GmTurnRequest(UUID turnId, GmInputRequest input, String ragCondition,
                                List<RagEvidenceRequest> ragEvidence) {
        RuntimeEvidenceOverride ragOverride() {
            List<RuntimeEvidence> evidence = (ragEvidence == null ? List.<RagEvidenceRequest>of() : ragEvidence).stream()
                    .map(item -> new RuntimeEvidence(item.type(), new com.dndmaster.adventure.domain.knowledge.KnowledgeDocumentId(item.documentId()),
                            item.extractionVersion(), item.locator(), item.excerpt(),
                            item.visibility(), item.disclosureEvent(), item.disclosureTurn() == null ? 0 : item.disclosureTurn())).toList();
            return new RuntimeEvidenceOverride(ragCondition, new EvidencePack(
                    evidence.stream().filter(item -> item.evidenceType() == RuntimeEvidenceType.STORYBOOK).toList(),
                    evidence.stream().filter(item -> item.evidenceType() == RuntimeEvidenceType.RULEBOOK).toList(),
                    evidence.stream().filter(item -> item.evidenceType() == RuntimeEvidenceType.RESOLUTION).toList()));
        }
    }
    public record RagEvidenceRequest(RuntimeEvidenceType type, UUID documentId, long extractionVersion,
                                     String locator, String excerpt, StoryEvidenceVisibility visibility,
                                     String disclosureEvent, Long disclosureTurn) {}

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
            long version) {
        static RuntimeTurnResponse from(RuntimeTurnResult result) {
            return from(result, null);
        }
        static RuntimeTurnResponse from(RuntimeTurnResult result, com.dndmaster.adventure.application.runtime.SessionEventRepository eventRepository) {
            var eventValues = eventRepository == null ? java.util.stream.Stream.<String>empty()
                    : eventRepository.after(result.turn().sessionId(), -1).stream()
                    .flatMap(event -> java.util.stream.Stream.of(event.type(), event.payload()));
            var events = eventValues
                    .filter(java.util.Objects::nonNull).collect(java.util.stream.Collectors.toSet());
            var visibleStory = PlayerVisibleStoryEvidence.project(result.turn().evidencePack().storybook(),
                    events, result.version()).stream().map(evidence -> "storybook:" + evidence.locator()).collect(java.util.stream.Collectors.toSet());
            var safeNarration = PlayerVisibleStoryEvidence.redactNarration(result.turn().plan().narration(),
                    result.turn().evidencePack().storybook(), events, result.version());
            var safeJudgment = PlayerVisibleStoryEvidence.redactNarration(result.turn().plan().judgment(),
                    result.turn().evidencePack().storybook(), events, result.version());
            var safeScene = PlayerVisibleStoryEvidence.redactNarration(result.context().currentScene(),
                    result.turn().evidencePack().storybook(), events, result.version());
            var publicRefs = result.turn().citations().stream()
                    .filter(reference -> !reference.startsWith("storybook:") || visibleStory.contains(reference)).toList();
            return new RuntimeTurnResponse(
                    result.turn().turnId(),
                    result.turn().adventureId().value(),
                    result.turn().scenarioPackageId(),
                    result.turn().bindingVersion(),
                    safeNarration,
                    safeJudgment,
                    safeScene,
                    publicRefs,
                    result.turn().warnings(),
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
            List<com.dndmaster.adventure.application.combat.CombatMapViewPort.Layer> layers, Long version) {
        static CombatMapResponse from(UUID adventureId, long sessionVersion, com.dndmaster.adventure.application.combat.CombatMapViewPort.View view) {
            return new CombatMapResponse(adventureId, "authoritative-map", sessionVersion, view.mapId(), view.grid(), view.tokens(), view.obstacles(), view.layers(), view.version());
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
