package com.dndmaster.adventure.api;

import com.dndmaster.adventure.application.combat.AdventureCombatApplicationService;
import com.dndmaster.adventure.application.combat.CombatActionCommand;
import com.dndmaster.adventure.application.combat.CombatActorRole;
import com.dndmaster.adventure.application.guidance.AnswerRuleInquiryCommand;
import com.dndmaster.adventure.application.guidance.RuleGuidanceApplicationService;
import com.dndmaster.adventure.application.progress.AdventureProgressApplicationService;
import com.dndmaster.adventure.application.runtime.RuntimeTurnApplicationService;
import com.dndmaster.adventure.application.runtime.AdventurePlayerProjection;
import com.dndmaster.adventure.application.runtime.GmTurnRepository;
import com.dndmaster.adventure.application.runtime.RuntimeTurnResult;
import com.dndmaster.adventure.application.runtime.SubmitRuntimeTurnCommand;
import com.dndmaster.adventure.application.saved.CreateAdventureCommand;
import com.dndmaster.adventure.application.saved.SavedAdventureApplicationService;
import com.dndmaster.adventure.application.session.AdventureAiRequestApplicationService;
import com.dndmaster.adventure.application.scenario.AdventureScenarioApplicationService;
import com.dndmaster.adventure.application.scenario.ScenarioUpload;
import com.dndmaster.adventure.domain.adventure.*;
import com.dndmaster.adventure.domain.inquiry.InquiryId;
import com.dndmaster.adventure.domain.ruleset.DndEdition;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import com.dndmaster.adventure.domain.runtime.GmTurn;
import com.dndmaster.adventure.application.combat.CombatMapPort;
import com.dndmaster.adventure.application.combat.CombatMapMoveCommand;
import com.dndmaster.adventure.application.combat.CombatMapPreviewCommand;
import com.dndmaster.adventure.application.combat.CombatMapPreviewPosition;
import com.dndmaster.adventure.application.combat.CombatMapPreviewResult;
import com.dndmaster.adventure.application.combat.CombatMapMoveResult;
import com.dndmaster.adventure.application.combat.CombatMapMovementStatus;
import com.dndmaster.adventure.application.combat.MovementPlacementModelPort;
import com.dndmaster.adventure.application.combat.CharacterCombatPort;
import com.dndmaster.adventure.application.combat.RuntimeCombatRejectionException;
import com.dndmaster.adventure.application.combat.CombatActionApplicationService;
import com.dndmaster.adventure.application.combat.CombatStartParticipantFactory;
import com.dndmaster.adventure.application.combat.CombatStartTransitionPolicy;
import com.dndmaster.adventure.application.combat.CombatMapPlayerTokenResolver;
import com.dndmaster.adventure.application.combat.CombatMapPreparationPort;
import com.dndmaster.adventure.application.combat.PendingMapMovementConfirmationRepository;
import com.dndmaster.adventure.application.ruleset.AppliedRuleSetApplicationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.dndmaster.adventure.domain.runtime.PendingMapMovementConfirmation;

@RestController
@RequestMapping
public class AdventureController {
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(AdventureController.class);
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
    private final CombatActionApplicationService combatActionService;
    private final AdventureScenarioApplicationService scenarioService;
    private final AuthenticatedPlayerResolver playerResolver;
    private final CombatMapPort combatMapPort;
    private com.dndmaster.adventure.application.combat.MapMovementCoordinator mapMovementCoordinator;
    private final com.dndmaster.adventure.application.combat.SpatialActionAuthorizationPort spatialActionAuthorizationPort;
    private final PendingMapMovementConfirmationRepository pendingMapMovementConfirmationRepository;
    private final CharacterCombatPort characterCombatPort;
    private final com.dndmaster.adventure.application.combat.CombatMapViewPort combatMapViewPort;
    private final CombatMapPreparationPort combatMapPreparationPort;
    private final com.dndmaster.adventure.application.scenario.compilation.ScenarioPackageRepository scenarioPackageRepository;
    private com.dndmaster.adventure.domain.scenario.StageArtifactRepository stageArtifactRepository;
    private final ObjectMapper objectMapper;
    private final com.dndmaster.adventure.application.combat.CombatLifecycleApplicationService combatLifecycleService;
    private final AppliedRuleSetApplicationService appliedRuleSetService;
    private com.dndmaster.adventure.application.session.AdventureSessionApplicationService adventureSessionService;
    private final AdventureAiRequestApplicationService aiRequestService;
    private com.dndmaster.adventure.dice.AdventureDicePorts initiativeDicePorts;
    private MovementPlacementModelPort movementPlacementModelPort = context ->
            new MovementPlacementModelPort.MovementPlacementProposal("UNRESOLVED", null, List.of(), "목적지를 해석하지 못했습니다. 지도에서 목적지를 선택해주세요.");

    @Autowired(required = false)
    void setMovementPlacementModelPort(MovementPlacementModelPort movementPlacementModelPort) {
        this.movementPlacementModelPort = Objects.requireNonNull(movementPlacementModelPort);
    }

    @Autowired(required = false)
    void setStageArtifactRepository(com.dndmaster.adventure.domain.scenario.StageArtifactRepository repository) {
        this.stageArtifactRepository = Objects.requireNonNull(repository);
    }

    @Autowired(required = false)
    void setAdventureSessionApplicationService(
            com.dndmaster.adventure.application.session.AdventureSessionApplicationService service) {
        this.adventureSessionService = Objects.requireNonNull(service);
    }

    public record AdventureMovementOperationResponse(UUID operationId, com.dndmaster.adventure.application.combat.CombatMapMovementStatus status, long version,
            List<CombatMapPreviewPosition> requestedPath, List<CombatMapPreviewPosition> traversedPath, CombatMapPreviewPosition finalPosition,
            List<String> publicEvents, String interruptionReason, String outcomeStatus,
            com.dndmaster.adventure.application.combat.CombatMapPendingCheck pendingCheck,
            com.dndmaster.adventure.application.combat.MovementFollowUpCommand followUp) {
        static AdventureMovementOperationResponse from(com.dndmaster.adventure.application.combat.CombatMapMoveResult result) {
            return new AdventureMovementOperationResponse(result.operationId(), result.status(), result.version(), result.requestedPath(), result.traversedPath(),
                    result.finalPosition(), result.publicEvents(), result.interruptionReason(), result.status().name(), result.pendingCheck(), result.followUp());
        }
    }

    public record PendingMapMovementResponse(UUID mapId, UUID tokenId, long mapVersion,
            List<PositionPayload> path, int distance, String fingerprint, List<PositionPayload> waypoints,
            String sourceText, PositionPayload destination, UUID pendingTurnId, UUID confirmationCommandId, boolean terminal) {
        static PendingMapMovementResponse from(PendingMapMovementConfirmation pending) {
            return new PendingMapMovementResponse(pending.mapId(), pending.tokenId(), pending.mapVersion(),
                    pending.path().stream().map(position -> new PositionPayload(position.x(), position.y())).toList(),
                    pending.distance(), pending.fingerprint(),
                    pending.waypoints().stream().map(position -> new PositionPayload(position.x(), position.y())).toList(), pending.sourceText(),
                    pending.destination() == null ? null : new PositionPayload(pending.destination().x(), pending.destination().y()), pending.pendingTurnId(),
                    pending.confirmationCommandId(), pending.terminal());
        }
    }

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
            CombatActionApplicationService combatActionService,
            AdventureScenarioApplicationService scenarioService,
            AuthenticatedPlayerResolver playerResolver,
            ObjectProvider<CombatMapPort> combatMapPort,
            ObjectProvider<com.dndmaster.adventure.application.combat.SpatialActionAuthorizationPort> spatialActionAuthorization,
            ObjectProvider<CharacterCombatPort> characterCombatPort,
            ObjectMapper objectMapper,
            ObjectProvider<com.dndmaster.adventure.application.combat.CombatMapViewPort> combatMapViewPort,
            ObjectProvider<CombatMapPreparationPort> combatMapPreparationPort,
            PendingMapMovementConfirmationRepository pendingMapMovementConfirmationRepository,
            com.dndmaster.adventure.application.scenario.compilation.ScenarioPackageRepository scenarioPackageRepository,
            com.dndmaster.adventure.application.combat.CombatLifecycleApplicationService combatLifecycleService,
            AppliedRuleSetApplicationService appliedRuleSetService,
            AdventureAiRequestApplicationService aiRequestService) {
        this.savedAdventureService = savedAdventureService;
        this.runtimeTurnService = runtimeTurnService;
        this.adventureRepository = adventureRepository;
        this.gmTurnFailureRecorder = gmTurnFailureRecorder;
        this.gmTurnRepository = gmTurnRepository;
        this.runtimeTurnRepository = runtimeTurnRepository;
        this.sessionEventRepository = sessionEventRepository;
        this.guidanceService = guidanceService;
        this.combatService = combatService;
        this.combatActionService = combatActionService;
        this.scenarioService = scenarioService;
        this.playerResolver = playerResolver;
        this.combatMapPort = combatMapPort.getIfAvailable(() -> command -> {
            throw new IllegalStateException("combat map gateway unavailable");
        });
        this.spatialActionAuthorizationPort = spatialActionAuthorization.getIfAvailable(
                com.dndmaster.adventure.application.combat.SpatialActionAuthorizationPort::requiredPlayerAction);
        this.mapMovementCoordinator = new com.dndmaster.adventure.application.combat.MapMovementCoordinator(this.combatMapPort,
                this.spatialActionAuthorizationPort);
        this.pendingMapMovementConfirmationRepository = Objects.requireNonNull(pendingMapMovementConfirmationRepository,
                "pending map movement confirmation repository must not be null");
        this.characterCombatPort = characterCombatPort.getIfAvailable(() -> command -> {
            throw new IllegalStateException("character combat gateway unavailable");
        });
        this.combatMapViewPort = combatMapViewPort.getIfAvailable(() -> (adventureId1, ownerId) -> java.util.Optional.empty());
        this.combatMapPreparationPort = combatMapPreparationPort.getIfAvailable(() -> new CombatMapPreparationPort() {
            @Override public UUID prepareInitial(AdventureId adventureId, UUID ownerPlayerId, RuleSetId ruleSetId,
                    com.dndmaster.adventure.domain.scenario.MapDefinition mapDefinition, int stagePosition) { return null; }
        });
        this.scenarioPackageRepository = scenarioPackageRepository;
        this.objectMapper = objectMapper;
        this.combatLifecycleService = combatLifecycleService;
        this.appliedRuleSetService = appliedRuleSetService;
        this.aiRequestService = Objects.requireNonNull(aiRequestService, "AI request service must not be null");
    }

    public void setInitiativeDicePorts(com.dndmaster.adventure.dice.AdventureDicePorts dicePorts) {
        this.initiativeDicePorts = Objects.requireNonNull(dicePorts);
        this.mapMovementCoordinator = new com.dndmaster.adventure.application.combat.MapMovementCoordinator(
                combatMapPort, dicePorts, new com.dndmaster.adventure.application.runtime.DefaultResolutionPort(),
                spatialActionAuthorizationPort, dicePorts::rollEnemyObservation);
    }

    /** Player read boundary; canonical runtime snapshots and ScenarioModel are intentionally absent. */
    @GetMapping("/api/v1/adventures/{adventureId}")
    AdventurePlayerProjection playerAdventure(@PathVariable UUID adventureId) {
        Adventure adventure = adventureRepository.findById(new AdventureId(adventureId)).orElseThrow();
        if (!adventure.ownerPlayerId().value().equals(playerResolver.playerId())) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN);
        }
        return AdventurePlayerProjection.from(adventure);
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

    @PostMapping("/api/v1/adventures/{adventureId}/turns")
    public ResponseEntity<?> submitTypedTurn(
            @PathVariable UUID adventureId,
            @RequestHeader("Idempotency-Key") UUID commandId,
            @RequestHeader("If-Match-Version") long expectedVersion,
            @RequestBody GmTurnRequest request) {
        UUID owner = playerResolver.playerId();
        // Do not hold a database transaction or advisory lock across the
        // provider call. RuntimeTurnApplicationService persists each lifecycle
        // step independently and enforces the adventure-version boundary;
        // keeping the controller transaction open can deadlock failure
        // recording when a provider request is interrupted.
        var adventure = adventureRepository.findById(new AdventureId(adventureId)).orElseThrow();
        adventure.reopen(new OwnerPlayerId(owner));
        var input = normalizeMapMovementInput(request.input()).toDomain();
        var existing = gmTurnRepository.findByCommandId(commandId);
        if (existing.isPresent()) {
            existing.get().assertSameCommand(input);
            if (input instanceof com.dndmaster.adventure.domain.runtime.GmInput.MapActionInput) {
                pendingMapMovementConfirmationRepository.deleteByAdventureId(adventureId, owner);
            }
            var prior = runtimeTurnRepository.findByCommandId(commandId).orElseThrow(
                    () -> new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT, "turn is still processing"));
            if (prior.lifecycle() == com.dndmaster.adventure.application.runtime.RuntimeTurnLifecycle.COMMITTING) {
                try {
                    var resumed = runtimeTurnService.resumeRuntimeTurn(prior.turnId(), commandId);
                    Adventure current = adventureRepository.findById(new AdventureId(adventureId)).orElseThrow();
                    RuntimeTurnResult recovered = new RuntimeTurnResult(resumed.turn(), current.currentContext(),
                            current.conversation(), current.version(), null, resumed.movementResult());
                    if (resumed.status() == com.dndmaster.adventure.application.runtime.RuntimeTurnCommitOrchestrator.Status.COMMITTED) {
                        publishRecoveredTurn(adventureId, recovered);
                    }
                    return ResponseEntity.accepted().body(RuntimeTurnResponse.from(recovered));
                } catch (RuntimeException exception) {
                    LOGGER.error("gm_turn_recovery_failed turnId={} commandId={} adventureId={} exceptionClass={} exceptionMessage={}",
                            prior.turnId(), commandId, adventureId, exception.getClass().getName(), exception.getMessage(), exception);
                    return ResponseEntity.status(org.springframework.http.HttpStatus.BAD_GATEWAY).body(runtimeTurnFailure(exception));
                }
            }
            var priorMovement = runtimeTurnService.movementResultForTurn(prior.turnId());
            if (existing.get().input() instanceof com.dndmaster.adventure.domain.runtime.GmInput.MapActionInput mapAction)
                recordExplorationMovementSpend(adventureId, mapAction, priorMovement);
            return ResponseEntity.accepted().body(RuntimeTurnResponse.from(new RuntimeTurnResult(
                    prior, prior.context(), prior.conversation(), prior.version(), null, priorMovement)));
        }
        var processingGmTurn = gmTurnRepository.findProcessingByAdventureId(adventureId);
        var orphanedPresentedTurn = processingGmTurn.flatMap(gmTurn ->
                runtimeTurnRepository.findByCommandId(gmTurn.commandId())
                        .filter(turn -> turn.lifecycle().isCommitted()));
        if (orphanedPresentedTurn.isPresent()) {
            com.dndmaster.adventure.application.runtime.RuntimeTurn prior = orphanedPresentedTurn.get();
            RuntimeTurnResult recovered = new RuntimeTurnResult(prior, adventure.currentContext(),
                    adventure.conversation(), adventure.version(), null,
                    runtimeTurnService.movementResultForTurn(prior.turnId()));
            publishRecoveredTurn(adventureId, recovered);
            return ResponseEntity.status(org.springframework.http.HttpStatus.CONFLICT)
                    .body(Map.of("error", "PREVIOUS_TURN_RECOVERED", "message", "이전 턴의 완료 처리를 복구했습니다. 현재 모험 상태를 확인한 뒤 다시 입력해주세요."));
        }
        var pendingCommit = runtimeTurnRepository.findAllByAdventureId(new AdventureId(adventureId)).stream()
                .filter(pending -> pending.lifecycle() == com.dndmaster.adventure.application.runtime.RuntimeTurnLifecycle.COMMITTING)
                .findFirst();
        if (pendingCommit.isPresent()) {
            var pending = pendingCommit.get();
            try {
                var resumed = runtimeTurnService.resumeRuntimeTurn(pending.turnId(), pending.commandId());
                Adventure current = adventureRepository.findById(new AdventureId(adventureId)).orElseThrow();
                RuntimeTurnResult recovered = new RuntimeTurnResult(resumed.turn(), current.currentContext(),
                        current.conversation(), current.version(), null, resumed.movementResult());
                if (resumed.status() == com.dndmaster.adventure.application.runtime.RuntimeTurnCommitOrchestrator.Status.COMMITTED) {
                    publishRecoveredTurn(adventureId, recovered);
                }
                boolean sameAction = pending.action().equals(input.actionText())
                        && (pending.expectedVersion() == null || pending.expectedVersion() == expectedVersion);
                if (sameAction) return ResponseEntity.accepted().body(RuntimeTurnResponse.from(recovered));
                return ResponseEntity.status(org.springframework.http.HttpStatus.CONFLICT)
                        .body(Map.of("error", "PREVIOUS_TURN_RECOVERED", "message", "이전 턴 복구가 끝났습니다. 현재 모험 상태를 확인한 뒤 다시 입력해주세요."));
            } catch (RuntimeException exception) {
                LOGGER.error("gm_turn_pending_commit_recovery_failed turnId={} commandId={} adventureId={} exceptionClass={} exceptionMessage={}",
                        pending.turnId(), pending.commandId(), adventureId, exception.getClass().getName(), exception.getMessage(), exception);
                gmTurnFailureRecorder.recordResultProcessingFailure(adventure.sessionId().value(), pending.turnId(),
                        pending.commandId(), expectedVersion, exception);
                return ResponseEntity.status(org.springframework.http.HttpStatus.BAD_GATEWAY).body(runtimeTurnFailure(exception));
            }
        }
        try (AdventureAiRequestApplicationService.Permit permit = aiRequestService.begin(
                adventure.sessionId(), new OwnerPlayerId(owner), commandId)) {
            GmTurn turn = GmTurn.start(request.turnId(), commandId, expectedVersion, input);
            gmTurnRepository.save(turn, adventureId);
            RuntimeTurnResult result;
            try {
                gmTurnRepository.save(turn.process(), adventureId);
                List<com.dndmaster.adventure.application.runtime.RuntimeTurnCommand> externalCommands = List.of();
                if (input instanceof com.dndmaster.adventure.domain.runtime.GmInput.MapActionInput mapAction) {
                    if (adventure.status() == AdventureStatus.ACTIVE && adventure.currentSituation() != null) {
                        externalCommands = List.of(prepareMapCommand(adventure, owner, request.turnId(), commandId, mapAction));
                    } else {
                        applyMapAction(adventure, owner, commandId, mapAction);
                    }
                }
                result = runtimeTurnService.submitTurn(new SubmitRuntimeTurnCommand(
                        new AdventureId(adventureId), new OwnerPlayerId(owner), request.turnId(), commandId,
                        input.actionText(), expectedVersion,
                        null, -1, !(input instanceof com.dndmaster.adventure.domain.runtime.GmInput.MetaQuestionInput), false, false,
                        externalCommands));
                if (input instanceof com.dndmaster.adventure.domain.runtime.GmInput.MapActionInput mapAction) {
                    recordExplorationMovementSpend(adventureId, mapAction, result.movementResult());
                }
                if (input instanceof com.dndmaster.adventure.domain.runtime.GmInput.MapActionInput) {
                    pendingMapMovementConfirmationRepository.deleteByAdventureId(adventureId, owner);
                }
            } catch (RuntimeException exception) {
                LOGGER.error("gm_turn_request_failed stage=GM_TURN_CONTROLLER turnId={} commandId={} adventureId={} exceptionClass={} exceptionMessage={}",
                        request.turnId(), commandId, adventureId, exception.getClass().getName(), exception.getMessage(), exception);
                boolean awaitingCommitRecovery = runtimeTurnRepository.findByCommandId(commandId)
                        .filter(pending -> pending.lifecycle() == com.dndmaster.adventure.application.runtime.RuntimeTurnLifecycle.COMMITTING)
                        .isPresent();
                if (awaitingCommitRecovery) {
                    gmTurnFailureRecorder.recordResultProcessingFailure(adventure.sessionId().value(), request.turnId(),
                            commandId, expectedVersion, exception);
                } else {
                    gmTurnFailureRecorder.record(turn, adventureId, adventure.sessionId().value(), exception, expectedVersion);
                }
                if (exception instanceof com.dndmaster.adventure.application.runtime.RuntimeGmInputLimitException) {
                    throw exception;
                }
                if (exception instanceof RuntimeCombatRejectionException
                        || exception instanceof ApiRequestGuard.ApiContractException
                        || exception instanceof com.dndmaster.adventure.application.combat.CombatMapMovementPreviewRejectedException) {
                    throw exception;
                }
                String message = exception.getMessage() == null ? "" : exception.getMessage();
                if (message.contains("ADVENTURE_VERSION_CONFLICT")) {
                    return ResponseEntity.status(org.springframework.http.HttpStatus.CONFLICT).build();
                }
                if (message.contains("GM_TURN_ALREADY_IN_PROGRESS")) {
                    return ResponseEntity.status(org.springframework.http.HttpStatus.CONFLICT).build();
                }
                return ResponseEntity.status(org.springframework.http.HttpStatus.BAD_GATEWAY).body(runtimeTurnFailure(exception));
            }
            if (result.turn().lifecycle() != com.dndmaster.adventure.application.runtime.RuntimeTurnLifecycle.COMMITTED) {
                return ResponseEntity.accepted().body(RuntimeTurnResponse.from(result));
            }
            try {
                String providerMetadata = "provider=" + result.turn().plan().provider()
                        + ";model=" + result.turn().plan().model()
                        + ";reasoning=" + result.turn().plan().reasoning()
                        + ";validation=accepted";
                gmTurnRepository.save(turn.process().commit(providerMetadata), adventureId);
                GmTurn committedTurn = turn.process().commit(providerMetadata);
                com.dndmaster.adventure.application.runtime.GmTurnCommitPolicy.requirePublishable(committedTurn, result.version());
                Adventure committedAdventure = adventureRepository.findById(new AdventureId(adventureId))
                        .orElseThrow(() -> new IllegalStateException("adventure disappeared after runtime commit"));
                boolean combatStartRequested = result.turn().plan().combatStartRequested();
                boolean mapEntryRequested = result.turn().plan().mapEntryRequested();
                LOGGER.info("gm_turn_committed adventureId={} turnId={} scene={} situationLocation={} mapEntryRequested={} combatStart={}",
                        adventureId, turn.turnId(), result.turn().plan().scene(),
                        committedAdventure.currentSituation() == null ? "" : committedAdventure.currentSituation().location(),
                        mapEntryRequested, combatStartRequested);
                if (com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.enabled()) {
                    LOGGER.info("dev_gm_turn_content turnId={} action={} narration={}",
                            turn.turnId(), turn.input().actionText(), result.turn().plan().narration());
                }
                com.dndmaster.adventure.domain.combat.CombatEncounter combat = null;
                if (combatStartRequested) {
                    var existingCombat = combatLifecycleService.findActiveEncounter(adventureId);
                    if (existingCombat.isEmpty() || existingCombat.orElseThrow().status()
                            == com.dndmaster.adventure.domain.combat.CombatEncounter.Status.PREPARING) {
                        CombatStartTransitionPolicy.requireCommittedCombatSituation(committedAdventure.currentSituation(),
                                result.turn().plan().combatEnemies());
                        combat = combatLifecycleService.startPreparingFromCommittedGmTurn(adventureId, committedTurn,
                                        CombatStartParticipantFactory.applyExplorationAwareness(CombatStartParticipantFactory.preparingFromPartyAndGmProposal(adventureId, adventure.party(),
                                                result.turn().plan().combatEnemies(), member -> characterCombatPort.displayName(
                                                        member.characterSheetId().value(), adventure.ownerPlayerId().value(),
                                                        adventure.sessionId().value()),
                                                member -> characterCombatPort.initiativeModifier(
                                                        member.characterSheetId().value(), adventure.ownerPlayerId().value(),
                                                        adventure.sessionId().value()),
                                                member -> characterCombatPort.movementSpeed(
                                                        member.characterSheetId().value(), adventure.ownerPlayerId().value(),
                                                        adventure.sessionId().value()),
                                                (participantId, scope) -> initiativeDicePorts.rollInitiative(adventureId,
                                                        adventure.ruleSetId().value(), adventure.sessionId().value(),
                                                        committedTurn.turnId(), participantId, adventure.version(), scope)),
                                                storedExplorationState(committedAdventure).cycle()),
                                        result.enemySheetPreparationRequest());
                    } else {
                        LOGGER.warn("gm_turn_combat_start_skipped adventureId={} reason=ACTIVE_COMBAT_ALREADY_EXISTS turnId={}",
                                adventureId, result.turn().turnId());
                    }
                    combatLifecycleService.findActiveEncounter(adventureId)
                            .ifPresent(encounter -> linkExplorationCycleToCombat(adventureId, encounter.encounterId()));
                } else if (!(turn.input() instanceof com.dndmaster.adventure.domain.runtime.GmInput.MetaQuestionInput)) {
                    resolvePendingEnvironmentEncounterWithoutCombat(adventureId, turn.turnId(), "committed_turn");
                }
                sessionEventRepository.append(new com.dndmaster.adventure.domain.runtime.event.SessionEvent(
                        result.turn().sessionId(), UUID.randomUUID(), result.version(), "GM_TURN_COMMITTED", result.turn().turnId().toString()));
                if (combat != null) {
                    permit.handOffToCombatFollowUp();
                }
                return ResponseEntity.accepted().body(RuntimeTurnResponse.from(result));
            } catch (RuntimeException exception) {
                LOGGER.error("gm_turn_result_processing_failed turnId={} commandId={} adventureId={} exceptionClass={} exceptionMessage={}",
                        request.turnId(), commandId, adventureId, exception.getClass().getName(), exception.getMessage(), exception);
                try {
                    gmTurnFailureRecorder.recordResultProcessingFailure(result.turn().sessionId(), result.turn().turnId(),
                            commandId, result.version(), exception);
                } catch (RuntimeException recordingFailure) {
                    LOGGER.error("gm_turn_result_processing_failure_recording_failed turnId={} commandId={} adventureId={} exceptionClass={}",
                            request.turnId(), commandId, adventureId, recordingFailure.getClass().getName(), recordingFailure);
                }
                return ResponseEntity.status(org.springframework.http.HttpStatus.BAD_GATEWAY)
                        .body(Map.of("error", "GM_TURN_RESULT_PROCESSING_FAILED",
                                "message", "최종 결과를 반영하는 중 오류가 발생했습니다. 현재 저장된 모험 상태를 다시 확인해주세요."));
            }
        }
    }

    @PostMapping("/api/v1/adventures/{adventureId}/turns/{pendingTurnId}/resume")
    public ResponseEntity<?> resumePendingTurn(@PathVariable UUID adventureId, @PathVariable UUID pendingTurnId,
            @RequestHeader("Idempotency-Key") UUID idempotencyKey) {
        UUID owner = playerResolver.playerId();
        Adventure adventure = adventureRepository.findById(new AdventureId(adventureId)).orElseThrow();
        if (!adventure.ownerPlayerId().value().equals(owner)) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN);
        }
        var pending = runtimeTurnRepository.findByTurnId(pendingTurnId).orElseThrow();
        if (!pending.adventureId().value().equals(adventureId)) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN);
        }
        if (!pending.commandId().equals(idempotencyKey)) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST,
                    "IDEMPOTENCY_KEY_MISMATCH");
        }
        var resumed = runtimeTurnService.resumeRuntimeTurn(pendingTurnId, idempotencyKey);
        if (resumed.status() == com.dndmaster.adventure.application.runtime.RuntimeTurnCommitOrchestrator.Status.REPAIR_REQUIRED) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.CONFLICT)
                    .body(Map.of("error", "RUNTIME_TURN_REPAIR_REQUIRED"));
        }
        Adventure current = adventureRepository.findById(new AdventureId(adventureId)).orElseThrow();
        RuntimeTurnResult responseResult = new RuntimeTurnResult(resumed.turn(), current.currentContext(), current.conversation(),
                current.version(), null, resumed.movementResult());
        if (resumed.status() == com.dndmaster.adventure.application.runtime.RuntimeTurnCommitOrchestrator.Status.COMMITTED) {
            publishRecoveredTurn(adventureId, responseResult);
        }
        return ResponseEntity.accepted().body(RuntimeTurnResponse.from(responseResult));
    }

    private void publishRecoveredTurn(UUID adventureId, RuntimeTurnResult result) {
        var existing = gmTurnRepository.findByCommandId(result.turn().commandId()).orElseThrow();
        if (existing.input() instanceof com.dndmaster.adventure.domain.runtime.GmInput.MapActionInput mapAction)
            recordExplorationMovementSpend(adventureId, mapAction, result.movementResult());
        if (existing.input() instanceof com.dndmaster.adventure.domain.runtime.GmInput.MapActionInput mapAction)
            recordExplorationMovementSpend(adventureId, mapAction, result.movementResult());
        boolean newlyCommitted = existing.status() != com.dndmaster.adventure.domain.runtime.GmTurnStatus.COMMITTED;
        GmTurn committed = existing;
        if (newlyCommitted) {
            String providerMetadata = "provider=" + result.turn().plan().provider()
                    + ";model=" + result.turn().plan().model()
                    + ";reasoning=" + result.turn().plan().reasoning()
                    + ";validation=accepted;recovery=forward";
            committed = existing.commit(providerMetadata);
            gmTurnRepository.save(committed, adventureId);
            sessionEventRepository.appendNext(result.turn().sessionId(), UUID.randomUUID(), "GM_TURN_COMMITTED",
                    result.turn().turnId().toString());
        }
        if (result.turn().plan().combatStartRequested()) {
            Adventure adventure = adventureRepository.findById(new AdventureId(adventureId)).orElseThrow();
            CombatStartTransitionPolicy.requireCommittedCombatSituation(adventure.currentSituation(),
                    result.turn().plan().combatEnemies());
            var activeEncounter = combatLifecycleService.findActiveEncounter(adventureId);
            if (activeEncounter.isEmpty() || activeEncounter.orElseThrow().status()
                    == com.dndmaster.adventure.domain.combat.CombatEncounter.Status.PREPARING) {
                var encounter = combatLifecycleService.startPreparingFromCommittedGmTurn(adventureId, committed,
                                CombatStartParticipantFactory.applyExplorationAwareness(CombatStartParticipantFactory.preparingFromPartyAndGmProposal(adventureId, adventure.party(),
                                        result.turn().plan().combatEnemies(), member -> characterCombatPort.displayName(
                                                member.characterSheetId().value(), adventure.ownerPlayerId().value(),
                                                adventure.sessionId().value()),
                                        member -> characterCombatPort.initiativeModifier(
                                                member.characterSheetId().value(), adventure.ownerPlayerId().value(),
                                                adventure.sessionId().value()),
                                        member -> characterCombatPort.movementSpeed(
                                                member.characterSheetId().value(), adventure.ownerPlayerId().value(),
                                                adventure.sessionId().value()),
                                                (participantId, scope) -> initiativeDicePorts.rollInitiative(adventureId,
                                                        adventure.ruleSetId().value(), adventure.sessionId().value(),
                                                        result.turn().turnId(), participantId, adventure.version(), scope)),
                                        storedExplorationState(adventure).cycle()),
                                result.enemySheetPreparationRequest());
            }
            combatLifecycleService.findActiveEncounter(adventureId)
                    .ifPresent(encounter -> linkExplorationCycleToCombat(adventureId, encounter.encounterId()));
        } else if (!(existing.input() instanceof com.dndmaster.adventure.domain.runtime.GmInput.MetaQuestionInput)) {
            resolvePendingEnvironmentEncounterWithoutCombat(adventureId, result.turn().turnId(), "recovered_turn");
        }
    }

    private void resolvePendingEnvironmentEncounterWithoutCombat(UUID adventureId, UUID turnId, String source) {
        Adventure snapshot = adventureRepository.findById(new AdventureId(adventureId)).orElseThrow();
        if (!snapshot.gameState().values().containsKey("explorationState")) return;
        var snapshotState = storedExplorationState(snapshot);
        var snapshotCycle = snapshotState.cycle();
        var snapshotProposal = snapshotCycle == null ? null : snapshotCycle.pendingEnvironmentProposal();
        if (snapshotCycle == null || snapshotProposal == null || !snapshotProposal.encounterRequested()
                || snapshotCycle.linkedCombatEncounterId() != null
                || snapshotCycle.phase() != com.dndmaster.adventure.domain.runtime.ExplorationCycle.Phase.ENVIRONMENT) return;
        Adventure adventure = adventureRepository.findByIdForUpdate(new AdventureId(adventureId)).orElseThrow();
        var state = storedExplorationState(adventure);
        var cycle = state.cycle();
        var proposal = cycle == null ? null : cycle.pendingEnvironmentProposal();
        if (cycle == null || proposal == null || !proposal.encounterRequested()
                || cycle.linkedCombatEncounterId() != null
                || cycle.phase() != com.dndmaster.adventure.domain.runtime.ExplorationCycle.Phase.ENVIRONMENT) return;
        String diagnosticContext = "adventureId=" + adventureId + " turnId=" + turnId + " cycleId=" + cycle.id()
                + " phaseId=" + cycle.currentPhaseCommandId() + " source=" + source + " narrationChars="
                + (proposal.narration() == null ? 0 : proposal.narration().length());
        long started = com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.begin(LOGGER,
                "pending_environment_encounter_resolve", diagnosticContext);
        try {
            var resolved = cycle.resolveEncounterWithoutCombat();
            adventure.updateExplorationState(adventure.ownerPlayerId(), adventure.version(), state.withCycle(resolved));
            adventureRepository.save(adventure);
            com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.complete(LOGGER,
                    "pending_environment_encounter_resolve", diagnosticContext + " expectedEncounterRequested=true"
                            + " actualEncounterRequested=" + resolved.pendingEnvironmentProposal().encounterRequested()
                            + " narrationPreserved=true environmentPhase=" + resolved.environmentPhase(), started);
        } catch (RuntimeException | Error failure) {
            com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.fail(LOGGER,
                    "pending_environment_encounter_resolve", diagnosticContext + " recoveryAction=retry_after_committed_turn", started, failure);
            throw failure;
        }
    }

    void linkExplorationCycleToCombat(UUID adventureId, UUID encounterId) {
        Adventure snapshot = adventureRepository.findById(new AdventureId(adventureId)).orElseThrow();
        if (!snapshot.gameState().values().containsKey("explorationState")) return;
        Adventure adventure = adventureRepository.findByIdForUpdate(new AdventureId(adventureId)).orElseThrow();
        var state = storedExplorationState(adventure);
        var cycle = state.cycle();
        if (cycle == null) return;
        UUID linkedId = cycle.linkedCombatEncounterId();
        if (linkedId != null) {
            if (!linkedId.equals(encounterId))
                throw new ApiRequestGuard.ApiContractException(409, "EXPLORATION_CYCLE_ALREADY_LINKED_TO_COMBAT");
            return;
        }
        var linked = cycle.linkCombatEncounter(encounterId);
        adventure.updateExplorationState(new OwnerPlayerId(adventure.ownerPlayerId().value()), adventure.version(), state.withCycle(linked));
        adventureRepository.save(adventure);
        LOGGER.info("exploration_combat_handoff_linked adventureId={} cycleId={} phaseId={} encounterId={} result=linked",
                adventureId, linked.id(), linked.currentPhaseCommandId(), encounterId);
    }

    private com.dndmaster.adventure.domain.runtime.ExplorationState resumeExplorationCycleAfterCombatIfEnded(
            Adventure adventure, com.dndmaster.adventure.domain.runtime.ExplorationState state) {
        var cycle = state.cycle();
        if (cycle == null || cycle.linkedCombatEncounterId() == null) return state;
        UUID encounterId = cycle.linkedCombatEncounterId();
        String context = "adventureId=" + adventure.id().value() + " cycleId=" + cycle.id()
                + " encounterId=" + encounterId + " phase=" + cycle.phase()
                + " elapsedSeconds=" + cycle.elapsedSeconds();
        long started = com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.begin(
                LOGGER, "exploration_combat_handoff_resume", context);
        try {
            if (combatLifecycleService.findActiveEncounter(adventure.id().value()).isPresent())
                throw new ApiRequestGuard.ApiContractException(409, "EXPLORATION_CYCLE_SUSPENDED_FOR_COMBAT");
            var resumed = cycle.resumeAfterCombat(encounterId);
            com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.complete(LOGGER,
                    "exploration_combat_handoff_resume", context + " result=resumed", started);
            return state.withCycle(resumed);
        } catch (RuntimeException failure) {
            com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.fail(LOGGER,
                    "exploration_combat_handoff_resume", context + " recovery=retry_after_combat_ends", started, failure);
            throw failure;
        }
    }

    private static Map<String, String> runtimeTurnFailure(RuntimeException exception) {
        String raw = exception.getMessage() == null ? "" : exception.getMessage();
        int marker = raw.indexOf("GM final validation failed:");
        String message = marker >= 0
                ? "턴 계획 검증 실패: " + raw.substring(marker + "GM final validation failed:".length()).trim()
                : raw.isBlank()
                        ? "모험 메시지를 처리하지 못했습니다. 잠시 후 다시 시도해주세요."
                        : "턴 처리 실패: " + raw.substring(0, Math.min(raw.length(), 500));
        return Map.of("error", "GM_TURN_FAILED_RETRYABLE", "message", message);
    }

    @PostMapping("/api/v1/adventures/{adventureId}/turns/{pendingTurnId}/roll")
    RuntimeTurnResponse submitPlayerRoll(@PathVariable UUID adventureId, @PathVariable UUID pendingTurnId,
            @RequestBody PlayerRollRequest request) {
        RuntimeTurnResult result = runtimeTurnService.submitPlayerRoll(new com.dndmaster.adventure.application.runtime.SubmitPlayerRollCommand(
                new AdventureId(adventureId), new OwnerPlayerId(playerResolver.playerId()), pendingTurnId,
                request.result(), request.expectedVersion()));
        if (result.turn().lifecycle().isCommitted()) {
            publishRecoveredTurn(adventureId, result);
        }
        return RuntimeTurnResponse.from(result);
    }

    @PostMapping("/api/v1/adventures/{adventureId}/rule-inquiries")
    RuleInquiryResponse answerRuleInquiry(
            @PathVariable UUID adventureId, @RequestBody RuleInquiryRequest request) {
        UUID authenticatedOwner = playerResolver.playerId();
        var adventure = adventureRepository.findById(new AdventureId(adventureId)).orElseThrow();
        if (!adventure.ownerPlayerId().value().equals(authenticatedOwner)) {
            throw new ApiRequestGuard.ApiContractException(403, "OWNERSHIP_DENIED");
        }
        RuleSetId requestedRuleSet = new RuleSetId(request.ruleSetId());
        if (!adventure.ruleSetId().equals(requestedRuleSet)) {
            throw new ApiRequestGuard.ApiContractException(400, "INVALID_RULE_SET");
        }
        AnswerRuleInquiryCommand command = new AnswerRuleInquiryCommand(
                new InquiryId(request.inquiryId()),
                new AdventureId(adventureId),
                requestedRuleSet,
                new OwnerPlayerId(authenticatedOwner),
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
                .orElseGet(() -> new CombatMapResponse(adventureId, "map-view", adventure.version(), null, null, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), null));
    }

    @GetMapping("/api/v1/adventures/{adventureId}/exploration-groups")
    ExplorationGroupsResponse explorationGroups(@PathVariable UUID adventureId) {
        Adventure adventure = ownedAdventure(adventureId);
        Object stored = adventure.gameState().values().get("explorationState");
        var state = stored == null ? initialExplorationState(adventure) : objectMapper.convertValue(stored, com.dndmaster.adventure.domain.runtime.ExplorationState.class);
        return new ExplorationGroupsResponse(adventure.version(), state.groups(), state.cycle());
    }

    @PostMapping("/api/v1/adventures/{adventureId}/exploration-cycle/start")
    @org.springframework.transaction.annotation.Transactional
    ExplorationGroupsResponse startExplorationCycle(@PathVariable UUID adventureId,
            @RequestBody ExplorationCycleStartRequest request) {
        if (request == null || request.expectedVersion() == null || request.commandId() == null)
            throw new ApiRequestGuard.ApiContractException(400, "INVALID_EXPLORATION_CYCLE_START");
        Adventure adventure = adventureRepository.findByIdForUpdate(new AdventureId(adventureId)).orElseThrow();
        UUID owner = playerResolver.playerId();
        if (!adventure.ownerPlayerId().value().equals(owner))
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN);
        var state = storedExplorationState(adventure);
        if (state.cycle() != null) {
            if (state.cycle().id().equals(request.commandId()))
                return new ExplorationGroupsResponse(adventure.version(), state.groups(), state.cycle());
            throw new ApiRequestGuard.ApiContractException(409, "EXPLORATION_CYCLE_ALREADY_ACTIVE");
        }
        if (adventure.version() != request.expectedVersion())
            throw new ApiRequestGuard.ApiContractException(409, "STALE_EXPLORATION_CYCLE");
        var story = adventure.storyRuntimeState();
        java.util.Optional<com.dndmaster.adventure.domain.scenario.SituationDefinition> activeSituation =
                story == null || story.isOpenPlay() || stageArtifactRepository == null ? java.util.Optional.empty()
                : stageArtifactRepository.findDetailedStage(adventure.id(), story.scenarioPackageId(), story.stageId(),
                        story.backboneRevision(), story.detailedStageRevision())
                        .stream().flatMap(stage -> stage.situations().stream())
                        .filter(candidate -> candidate.situationId().equals(story.activeSituationId())).findFirst();
        boolean mapNeeded = activeSituation.map(com.dndmaster.adventure.domain.scenario.SituationDefinition::mapNeeded).orElse(false);
        boolean activeCombat = combatLifecycleService.findActiveEncounter(adventureId).isPresent();
        String eligibilityContext = "adventureId=" + adventureId + " groupCount=" + state.groups().size()
                + " expectedMapNeeded=true actualMapNeeded=" + mapNeeded + " activeCombat=" + activeCombat
                + " expectedVersion=" + request.expectedVersion();
        long eligibilityStarted = com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.begin(
                LOGGER, "exploration_cycle_start_eligibility", eligibilityContext);
        try {
            if (!mapNeeded)
                throw new ApiRequestGuard.ApiContractException(409, "EXPLORATION_REQUIRES_MAP_NEEDED_SITUATION");
            if (activeCombat)
                throw new ApiRequestGuard.ApiContractException(409, "EXPLORATION_CYCLE_SUSPENDED_FOR_COMBAT");
            com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.complete(LOGGER,
                    "exploration_cycle_start_eligibility", eligibilityContext + " result=eligible", eligibilityStarted);
        } catch (RuntimeException | Error failure) {
            com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.fail(LOGGER,
                    "exploration_cycle_start_eligibility", eligibilityContext + " recoveryAction=resolve_situation_or_end_combat",
                    eligibilityStarted, failure);
            throw failure;
        }
        if (initiativeDicePorts == null)
            throw new ApiRequestGuard.ApiContractException(503, "EXPLORATION_INITIATIVE_UNAVAILABLE");
        long started = com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.begin(LOGGER,
                "exploration_cycle_start", "adventureId=" + adventureId + " groupCount=" + state.groups().size()
                        + " expectedVersion=" + request.expectedVersion());
        try {
            var scores = new java.util.LinkedHashMap<UUID, Integer>();
            for (var group : state.groups()) {
                int score = group.members().stream().filter(member -> member.formation()
                                == com.dndmaster.adventure.domain.runtime.ExplorationGroup.Formation.FRONT)
                        .mapToInt(member -> initiativeDicePorts.rollInitiative(adventureId, adventure.ruleSetId().value(),
                                        adventure.sessionId().value(), request.commandId(), member.characterSheetId(), adventure.version(),
                                        com.dndmaster.adventure.dice.domain.RollScope.PLAYER_ACTION)
                                + characterCombatPort.initiativeModifier(member.characterSheetId(), owner, adventure.sessionId().value()))
                        .max().orElseThrow(() -> new ApiRequestGuard.ApiContractException(409, "EXPLORATION_GROUP_FRONT_MEMBER_REQUIRED"));
                scores.put(group.id(), score);
            }
            long seed = request.commandId().getMostSignificantBits() ^ request.commandId().getLeastSignificantBits();
            var cycle = com.dndmaster.adventure.domain.runtime.ExplorationCycle.start(request.commandId(), scores,
                    new java.util.Random(seed));
            adventure.updateExplorationState(new OwnerPlayerId(owner), request.expectedVersion(), state.withCycle(cycle));
            adventureRepository.save(adventure);
            com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.complete(LOGGER,
                    "exploration_cycle_start", "adventureId=" + adventureId + " cycleId=" + cycle.id()
                            + " groupCount=" + scores.size() + " order=" + cycle.groupOrder()
                            + " expectedVersion=" + request.expectedVersion() + " actualVersion=" + adventure.version(), started);
            return new ExplorationGroupsResponse(adventure.version(), state.groups(), cycle);
        } catch (RuntimeException | Error failure) {
            com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.fail(LOGGER,
                    "exploration_cycle_start", "adventureId=" + adventureId + " groupCount=" + state.groups().size()
                            + " expectedVersion=" + request.expectedVersion() + " actualVersion=" + adventure.version(), started, failure);
            throw failure;
        }
    }

    @PostMapping("/api/v1/adventures/{adventureId}/exploration-cycle/groups/{groupId}/finish")
    @org.springframework.transaction.annotation.Transactional
    ExplorationGroupsResponse finishExplorationGroupTurn(@PathVariable UUID adventureId, @PathVariable UUID groupId,
            @RequestBody ExplorationCycleFinishRequest request) {
        if (request == null || request.expectedVersion() == null || request.commandId() == null || request.interrupted() == null)
            throw new ApiRequestGuard.ApiContractException(400, "INVALID_EXPLORATION_GROUP_TURN_FINISH");
        Adventure adventure = adventureRepository.findByIdForUpdate(new AdventureId(adventureId)).orElseThrow();
        UUID owner = playerResolver.playerId();
        if (!adventure.ownerPlayerId().value().equals(owner))
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN);
        var state = storedExplorationState(adventure);
        state = resumeExplorationCycleAfterCombatIfEnded(adventure, state);
        if (state.cycle() == null) throw new ApiRequestGuard.ApiContractException(409, "EXPLORATION_CYCLE_NOT_STARTED");
        if (request.interrupted()) throw new ApiRequestGuard.ApiContractException(409, "MOVEMENT_INTERRUPTION_MUST_COME_FROM_MAP_RESULT");
        if (state.cycle().groupTurnFinished(groupId))
            return new ExplorationGroupsResponse(adventure.version(), state.groups(), state.cycle());
        if (adventure.version() != request.expectedVersion())
            throw new ApiRequestGuard.ApiContractException(409, "STALE_EXPLORATION_CYCLE");
        var cycle = state.cycle().finishGroupTurn(groupId, false);
        adventure.updateExplorationState(new OwnerPlayerId(owner), request.expectedVersion(), state.withCycle(cycle));
        adventureRepository.save(adventure);
        return new ExplorationGroupsResponse(adventure.version(), state.groups(), cycle);
    }

    @PostMapping("/api/v1/adventures/{adventureId}/exploration-cycle/events/{eventId}/resolve")
    @org.springframework.transaction.annotation.Transactional
    ExplorationGroupsResponse resolveExplorationMovementEvent(@PathVariable UUID adventureId, @PathVariable UUID eventId,
            @RequestBody ExplorationMovementEventResolveRequest request) {
        if (request == null || request.expectedVersion() == null)
            throw new ApiRequestGuard.ApiContractException(400, "INVALID_EXPLORATION_MOVEMENT_EVENT_RESOLUTION");
        Adventure adventure = adventureRepository.findByIdForUpdate(new AdventureId(adventureId)).orElseThrow();
        UUID owner = playerResolver.playerId();
        if (!adventure.ownerPlayerId().value().equals(owner))
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN);
        var state = storedExplorationState(adventure);
        state = resumeExplorationCycleAfterCombatIfEnded(adventure, state);
        if (state.cycle() == null) throw new ApiRequestGuard.ApiContractException(409, "EXPLORATION_CYCLE_NOT_STARTED");
        if (state.cycle().movementEventResolved(eventId))
            return new ExplorationGroupsResponse(adventure.version(), state.groups(), state.cycle());
        if (state.cycle().pendingMovementEvent() == null || !state.cycle().pendingMovementEvent().id().equals(eventId))
            throw new ApiRequestGuard.ApiContractException(409, "EXPLORATION_MOVEMENT_EVENT_NOT_PENDING");
        if (adventure.version() != request.expectedVersion())
            throw new ApiRequestGuard.ApiContractException(409, "STALE_EXPLORATION_CYCLE");
        long started = com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.begin(LOGGER,
                "exploration_movement_event_resolve", "adventureId=" + adventureId + " cycleId=" + state.cycle().id()
                        + " eventId=" + eventId + " pendingEventId=" + state.cycle().pendingMovementEvent().id()
                        + " groupId=" + state.cycle().pendingMovementEvent().groupId()
                        + " expectedVersion=" + request.expectedVersion());
        try {
            var cycle = state.cycle().resolveMovementEvent(eventId);
            adventure.updateExplorationState(new OwnerPlayerId(owner), request.expectedVersion(), state.withCycle(cycle));
            adventureRepository.save(adventure);
            com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.complete(LOGGER,
                    "exploration_movement_event_resolve", "adventureId=" + adventureId + " cycleId=" + cycle.id()
                            + " eventId=" + eventId + " expectedVersion=" + request.expectedVersion()
                            + " actualVersion=" + adventure.version() + " nextGroupId=" + cycle.currentGroupId()
                            + " nextPhase=" + cycle.phase() + " result=resolved", started);
            return new ExplorationGroupsResponse(adventure.version(), state.groups(), cycle);
        } catch (RuntimeException | Error failure) {
            com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.fail(LOGGER,
                    "exploration_movement_event_resolve", "adventureId=" + adventureId + " cycleId=" + state.cycle().id()
                            + " eventId=" + eventId + " expectedVersion=" + request.expectedVersion()
                            + " actualVersion=" + adventure.version() + " result=failed recoveryAction=retry_event_id", started, failure);
            throw failure;
        }
    }

    private com.dndmaster.adventure.domain.runtime.ExplorationState storedExplorationState(Adventure adventure) {
        Object stored = adventure.gameState().values().get("explorationState");
        return stored == null ? initialExplorationState(adventure)
                : objectMapper.convertValue(stored, com.dndmaster.adventure.domain.runtime.ExplorationState.class);
    }

    private com.dndmaster.adventure.domain.runtime.ExplorationState initialExplorationState(Adventure adventure) {
        var map = combatMapViewPort.playerView(adventure.id().value(), playerResolver.playerId())
                .orElseThrow(() -> new ApiRequestGuard.ApiContractException(409, "EXPLORATION_MAP_UNAVAILABLE"));
        var playerToken = map.tokens().stream().filter(token -> "PLAYER".equals(token.type())).findFirst()
                .orElseThrow(() -> new ApiRequestGuard.ApiContractException(409, "EXPLORATION_PLAYER_TOKEN_UNAVAILABLE"));
        var members = adventure.party().stream().map(member -> new com.dndmaster.adventure.domain.runtime.ExplorationGroup.Member(
                member.characterSheetId().value(), com.dndmaster.adventure.domain.runtime.ExplorationGroup.Formation.FRONT,
                characterCombatPort.movementSpeed(member.characterSheetId().value(), playerResolver.playerId(), adventure.sessionId().value()))).toList();
        UUID groupId = UUID.nameUUIDFromBytes((adventure.id().value() + ":exploration-group").getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return new com.dndmaster.adventure.domain.runtime.ExplorationState(List.of(
                new com.dndmaster.adventure.domain.runtime.ExplorationGroup(groupId, playerToken.id(), members)));
    }

    @PostMapping("/api/v1/adventures/{adventureId}/exploration-cycle/environment/advance")
    ExplorationEnvironmentPhaseResponse advanceExplorationEnvironmentPhase(@PathVariable UUID adventureId,
            @RequestBody ExplorationCycleFinishRequest request) {
        if (request == null || request.expectedVersion() == null || request.commandId() == null)
            throw new ApiRequestGuard.ApiContractException(400, "INVALID_EXPLORATION_ENVIRONMENT_PHASE");
        UUID owner = playerResolver.playerId();
        Adventure adventure = ownedAdventure(adventureId);
        var state = storedExplorationState(adventure);
        state = resumeExplorationCycleAfterCombatIfEnded(adventure, state);
        var cycle = state.cycle();
        if (cycle == null || cycle.phase() != com.dndmaster.adventure.domain.runtime.ExplorationCycle.Phase.ENVIRONMENT)
            throw new ApiRequestGuard.ApiContractException(409, "EXPLORATION_ENVIRONMENT_PHASE_NOT_ACTIVE");
        if (cycle.pendingEnvironmentProposal() != null && cycle.pendingEnvironmentProposal().encounterRequested())
            throw new ApiRequestGuard.ApiContractException(409, "EXPLORATION_ENCOUNTER_RESOLUTION_PENDING");
        if (combatLifecycleService.findActiveEncounter(adventureId).isPresent())
            throw new ApiRequestGuard.ApiContractException(409, "EXPLORATION_CYCLE_SUSPENDED_FOR_COMBAT");
        UUID phaseId = cycle.currentPhaseCommandId();
        if (!phaseId.equals(request.commandId())) throw new ApiRequestGuard.ApiContractException(409, "EXPLORATION_ENVIRONMENT_PHASE_ID_MISMATCH");
        if (cycle.pendingEnvironmentProposal() == null && adventure.version() != request.expectedVersion())
            throw new ApiRequestGuard.ApiContractException(409, "STALE_EXPLORATION_CYCLE");
        List<UUID> expectedFeatureIds = cycle.pendingEnvironmentProposal() == null ? List.of()
                : cycle.pendingEnvironmentProposal().featureInteractions().stream()
                        .map(com.dndmaster.adventure.domain.runtime.ExplorationCycle.FeatureInteraction::featureId).toList();
        long started = com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.begin(LOGGER,
                "exploration_environment_phase", "adventureId=" + adventureId + " phaseId=" + phaseId
                        + " cycle=" + cycle.cycleNumber() + " phase=" + cycle.environmentPhase()
                        + " mapVersionExpected=" + request.expectedVersion()
                        + " expectedEnemyMoves=" + (cycle.pendingEnvironmentProposal() == null ? "pending"
                                : cycle.pendingEnvironmentProposal().enemyMoves().size())
                        + " expectedFeatureInteractions=" + (cycle.pendingEnvironmentProposal() == null ? "pending"
                                : cycle.pendingEnvironmentProposal().featureInteractions().size())
                        + " expectedFeatureIds=" + expectedFeatureIds
                        + " completedActions=" + cycle.completedEnvironmentActions().size());
        long actualMapVersion = -1;
        int actualEnemyMoves = cycle.completedEnvironmentActions().size();
        int actualFeatureInteractions = 0;
        try {
            var map = combatMapViewPort.playerView(adventureId, owner)
                    .orElseThrow(() -> new ApiRequestGuard.ApiContractException(409, "EXPLORATION_MAP_UNAVAILABLE"));
            actualMapVersion = map.version();
            var proposal = cycle.pendingEnvironmentProposal();
            if (proposal == null) {
                String gmMap = combatMapViewPort.gmEnvironmentView(map.mapId(), owner);
                var groupFacts = state.groups().stream().map(group -> Map.of("groupId", group.id(),
                        "members", group.members().stream().map(member -> Map.of("characterSheetId", member.characterSheetId(),
                                "formation", member.formation(), "speed", member.speed())).toList())).toList();
                int mapStateOffset = gmMap.indexOf(" GM_MAP_STATE=");
                var eligibleEnemyTokenIds = hostileTokenIds(gmMap);
                String discoveryContext = mapStateOffset < 0
                        ? gmMap + "\nEXPLORATION_GROUPS=" + objectMapper.valueToTree(groupFacts)
                        : gmMap.substring(0, mapStateOffset) + "EXPLORATION_GROUPS=" + objectMapper.valueToTree(groupFacts)
                                + "\n" + gmMap.substring(mapStateOffset);
                discoveryContext += "\nELIGIBLE_HOSTILE_TOKEN_IDS=" + objectMapper.valueToTree(eligibleEnemyTokenIds);
                var generated = runtimeTurnService.planExplorationEnvironmentPhase(new AdventureId(adventureId),
                        new OwnerPlayerId(owner), phaseId, cycle.environmentPhase(), discoveryContext);
                if (generated.encounterRequested() != generated.hostileActionStarted())
                {
                    if (com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.enabled())
                        LOGGER.info("dev_exploration_threat_discovery outcome=failed adventureId={} phaseId={} expectedHandoffMatchesHostileAction=true actualEncounterRequested={} actualHostileActionStarted={} failure=handoff_mismatch recovery=retry_same_phase",
                                adventureId, phaseId, generated.encounterRequested(), generated.hostileActionStarted());
                    throw new ApiRequestGuard.ApiContractException(502, "COMBAT_HANDOFF_REQUIRES_HOSTILE_ACTION");
                }
                proposal = new com.dndmaster.adventure.domain.runtime.ExplorationCycle.EnvironmentProposal(
                        generated.narration(), generated.enemyMoves(), generated.featureInteractions(), map.version(),
                        generated.encounterRequested(), null, null, java.util.Set.of(),
                        generated.hostileActionStarted());
                if (generated.threatDiscovery() != null) {
                    var typed = generated.threatDiscovery();
                    if (!eligibleEnemyTokenIds.containsAll(typed.awareEnemyTokenIds())
                            || (typed.bothSidesNotHidden() && !new java.util.HashSet<>(typed.awareEnemyTokenIds()).equals(eligibleEnemyTokenIds))
                            || (typed.enemyDiscoveredFirst() && typed.awareEnemyTokenIds().isEmpty()))
                        throw new ApiRequestGuard.ApiContractException(502, "UNKNOWN_AWARE_ENEMY_TOKEN_ID");
                    if (typed.enemyHeardNotification()) {
                        throw new ApiRequestGuard.ApiContractException(502, "ENEMY_AWARENESS_REQUIRES_EXPLICIT_NOTIFICATION");
                    }
                    var discovery = new com.dndmaster.adventure.domain.runtime.ExplorationCycle.ThreatDiscovery(
                            new java.util.HashSet<>(typed.discovererCharacterSheetIds()), typed.enemyDiscoveredFirst(),
                            typed.enemyHeardNotification(), typed.bothSidesNotHidden(),
                            new java.util.HashSet<>(typed.awareEnemyTokenIds()), eligibleEnemyTokenIds);
                    var partyIds = adventure.party().stream().map(member -> member.characterSheetId().value())
                            .collect(java.util.stream.Collectors.toSet());
                    proposal = proposal.withDiscovery(discovery, partyIds);
                    if (com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.enabled()) {
                        LOGGER.info("dev_exploration_threat_discovery outcome=accepted adventureId={} phaseId={} discovererCount={} awareEnemyCount={} enemyDiscoveredFirst={} enemyHeardNotification={} bothSidesNotHidden={} encounterRequested={} result=private_state_saved",
                                adventureId, phaseId, discovery.discovererCharacterSheetIds().size(), discovery.awareEnemyTokenIds().size(),
                                discovery.enemyDiscoveredFirst(), discovery.enemyHeardNotification(), discovery.bothSidesNotHidden(), generated.encounterRequested());
                    }
                } else {
                    if (com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.enabled()) {
                        LOGGER.info("dev_exploration_threat_discovery outcome=not_found adventureId={} phaseId={} result=awareness_unchanged",
                                adventureId, phaseId);
                    }
                }
                expectedFeatureIds = proposal.featureInteractions().stream()
                        .map(com.dndmaster.adventure.domain.runtime.ExplorationCycle.FeatureInteraction::featureId).toList();
                if (cycle.environmentPhase() != com.dndmaster.adventure.domain.runtime.ExplorationCycle.EnvironmentPhase.ENEMIES_PATROLS_AND_DETECTION
                        && !proposal.enemyMoves().isEmpty()) {
                    throw new ApiRequestGuard.ApiContractException(502, "ENEMY_MOVEMENT_OUTSIDE_ENVIRONMENT_MOVEMENT_PHASE");
                }
                if (cycle.environmentPhase() != com.dndmaster.adventure.domain.runtime.ExplorationCycle.EnvironmentPhase.HAZARDS_AND_ENVIRONMENT
                        && !proposal.featureInteractions().isEmpty()) {
                    throw new ApiRequestGuard.ApiContractException(502, "FEATURE_INTERACTION_OUTSIDE_HAZARDS_PHASE");
                }
                int distanceUnit = map.grid().distanceUnit();
                for (var move : proposal.enemyMoves()) {
                    int distance = (move.path().size() - 1) * distanceUnit;
                    var command = environmentMovementCommand(map.mapId(), owner, move, distance,
                            appliedEdition(adventure).edition(), map.version(), phaseId);
                    combatMapPort.validateEnemyForEnvironment(command);
                }
                for (var interaction : proposal.featureInteractions()) {
                    combatMapPort.validateSpatialFeatureInteractionForEnvironment(map.mapId(), owner,
                            interaction.featureId(), map.version(), environmentFeatureActionId(phaseId, interaction.featureId()));
                }
                cycle = cycle.withEnvironmentProposal(proposal);
                adventure.updateExplorationState(new OwnerPlayerId(owner), adventure.version(), state.withCycle(cycle));
                adventureRepository.save(adventure);
                state = state.withCycle(cycle);
            }
            if (cycle.environmentPhase() != com.dndmaster.adventure.domain.runtime.ExplorationCycle.EnvironmentPhase.HAZARDS_AND_ENVIRONMENT
                    && !proposal.featureInteractions().isEmpty()) {
                throw new ApiRequestGuard.ApiContractException(502, "FEATURE_INTERACTION_OUTSIDE_HAZARDS_PHASE");
            }
            long version = proposal.expectedMapVersion();
            int distanceUnit = map.grid().distanceUnit();
            for (int index = 0; index < proposal.enemyMoves().size(); index++) {
                var move = proposal.enemyMoves().get(index);
                UUID actionId = environmentActionId(phaseId, move.tokenId());
                if (cycle.completedEnvironmentActions().contains(actionId)) { version++; continue; }
                var command = environmentMovementCommand(map.mapId(), owner, move,
                        (move.path().size() - 1) * distanceUnit, appliedEdition(adventure).edition(), version,
                        phaseId);
                version = combatMapPort.moveEnemyForEnvironment(command);
                actualMapVersion = version;
                actualEnemyMoves++;
                cycle = cycle.completeEnvironmentAction(actionId);
                Adventure latest = ownedAdventure(adventureId);
                latest.updateExplorationState(new OwnerPlayerId(owner), latest.version(), state.withCycle(cycle));
                adventureRepository.save(latest);
                state = state.withCycle(cycle);
            }
            for (var interaction : proposal.featureInteractions()) {
                UUID actionId = environmentFeatureActionId(phaseId, interaction.featureId());
                if (cycle.completedEnvironmentActions().contains(actionId)) { version++; continue; }
                var applied = combatMapPort.interactSpatialFeatureForEnvironment(map.mapId(), owner,
                        interaction.featureId(), version, actionId);
                version = applied.version();
                actualMapVersion = version;
                actualFeatureInteractions++;
                cycle = cycle.completeEnvironmentAction(actionId);
                Adventure latest = ownedAdventure(adventureId);
                latest.updateExplorationState(new OwnerPlayerId(owner), latest.version(), state.withCycle(cycle));
                adventureRepository.save(latest);
                state = state.withCycle(cycle);
            }
            if (proposal.encounterRequested()) {
                com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.complete(LOGGER,
                        "exploration_environment_phase", "adventureId=" + adventureId + " phaseId=" + phaseId
                                + " encounterRequested=true enemyMovesExpected=" + proposal.enemyMoves().size()
                                + " enemyMovesApplied=" + actualEnemyMoves + " elapsedSeconds=" + cycle.elapsedSeconds()
                                + " featureInteractionsExpected=" + proposal.featureInteractions().size()
                                + " featureInteractionsApplied=" + actualFeatureInteractions
                                + " featureIds=" + expectedFeatureIds
                                + " result=handoff_pending recoveryAction=await_committed_gm_combat_start", started);
                Adventure latest = ownedAdventure(adventureId);
                return new ExplorationEnvironmentPhaseResponse(latest.version(), cycle, proposal.narration());
            }
            if (cycle.environmentPhase() == com.dndmaster.adventure.domain.runtime.ExplorationCycle.EnvironmentPhase.DURATIONS_AND_DEADLINES) {
                UUID durationId = UUID.nameUUIDFromBytes((phaseId + ":durations").getBytes(java.nio.charset.StandardCharsets.UTF_8));
                var currentMap = combatMapViewPort.playerView(adventureId, owner).orElseThrow();
                var duration = combatMapPort.advanceDurations(new com.dndmaster.adventure.application.combat.CombatMapSpatialTurnCommand(currentMap.mapId(), owner,
                        currentMap.version(), durationId));
                actualMapVersion = duration.version();
                mapMovementCoordinator.advanceGameTime(new com.dndmaster.adventure.application.combat.CombatMapGameTimeCommand(adventureId, currentMap.mapId(), owner,
                        cycle.cycleNumber(), duration.version(), phaseId));
            }
            cycle = cycle.completeEnvironmentPhase(phaseId);
            Adventure latest = ownedAdventure(adventureId);
            latest.updateExplorationState(new OwnerPlayerId(owner), latest.version(), state.withCycle(cycle));
            adventureRepository.save(latest);
            com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.complete(LOGGER,
                    "exploration_environment_phase", "adventureId=" + adventureId + " phaseId=" + phaseId
                            + " cycle=" + cycle.cycleNumber() + " phase=" + cycle.environmentPhase()
                            + " expectedEnemyMoves=" + proposal.enemyMoves().size()
                            + " actualEnemyMoves=" + actualEnemyMoves
                            + " expectedFeatureInteractions=" + proposal.featureInteractions().size()
                            + " actualFeatureInteractions=" + actualFeatureInteractions
                            + " featureIds=" + expectedFeatureIds
                            + " mapVersionExpected=" + proposal.expectedMapVersion()
                            + " mapVersionActual=" + actualMapVersion
                            + " elapsedSeconds=" + cycle.elapsedSeconds()
                            + " encounterRequested=" + proposal.encounterRequested()
                            + " recoveryAction=continue_next_group_cycle", started);
            return new ExplorationEnvironmentPhaseResponse(latest.version(), cycle, proposal.narration());
        } catch (RuntimeException | Error failure) {
            com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.fail(LOGGER,
                    "exploration_environment_phase", "adventureId=" + adventureId + " phaseId=" + phaseId
                            + " cycle=" + cycle.cycleNumber() + " phase=" + cycle.environmentPhase()
                            + " mapVersionExpected=" + request.expectedVersion() + " mapVersionActual=" + actualMapVersion
                            + " expectedEnemyMoves=" + (cycle.pendingEnvironmentProposal() == null ? "pending"
                                    : cycle.pendingEnvironmentProposal().enemyMoves().size())
                            + " actualEnemyMoves=" + actualEnemyMoves
                            + " expectedFeatureInteractions=" + (cycle.pendingEnvironmentProposal() == null ? "pending"
                                    : cycle.pendingEnvironmentProposal().featureInteractions().size())
                            + " actualFeatureInteractions=" + actualFeatureInteractions
                            + " expectedFeatureIds=" + expectedFeatureIds
                            + " elapsedSeconds=" + cycle.elapsedSeconds()
                            + " pendingProposal=" + (cycle.pendingEnvironmentProposal() != null)
                            + " recoveryAction=retry_same_phase_id", started, failure);
            throw failure;
        }
    }

    private static UUID environmentActionId(UUID phaseId, UUID tokenId) {
        return UUID.nameUUIDFromBytes((phaseId + ":enemy:" + tokenId).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
    private static UUID environmentFeatureActionId(UUID phaseId, UUID featureId) {
        return UUID.nameUUIDFromBytes((phaseId + ":feature-interaction:" + featureId).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
    private static java.util.Set<UUID> hostileTokenIds(String gmMap) {
        try {
            int marker = gmMap.indexOf(" GM_MAP_STATE=");
            if (marker < 0) return java.util.Set.of();
            var root = new ObjectMapper().readTree(gmMap.substring(marker + " GM_MAP_STATE=".length()));
            var tokens = root.path("tokens");
            if (!tokens.isArray()) throw new IllegalArgumentException("current map has no token list");
            var ids = new java.util.LinkedHashSet<UUID>();
            for (var token : tokens) {
                String type = token.path("type").asText("");
                if (!"ENEMY".equalsIgnoreCase(type) && !"BOSS".equalsIgnoreCase(type)) continue;
                UUID id = UUID.fromString(token.path("id").asText());
                if (!ids.add(id)) throw new IllegalArgumentException("current map has duplicate hostile token ids");
            }
            return java.util.Set.copyOf(ids);
        } catch (java.io.IOException | IllegalArgumentException failure) {
            throw new ApiRequestGuard.ApiContractException(502, "CURRENT_HOSTILE_TOKEN_LIST_INVALID");
        }
    }

    private static com.dndmaster.adventure.application.combat.CombatMapEnemyMovementCommand environmentMovementCommand(UUID mapId, UUID owner,
            com.dndmaster.adventure.domain.runtime.ExplorationCycle.EnemyMove move, int distance, String edition,
            long expectedMapVersion, UUID phaseId) {
        UUID actionId = environmentActionId(phaseId, move.tokenId());
        return new com.dndmaster.adventure.application.combat.CombatMapEnemyMovementCommand(mapId, owner, move.tokenId(), move.path().stream()
                .map(cell -> new com.dndmaster.adventure.application.combat.CombatMapEnemyMovementCommand.Position(cell.x(), cell.y())).toList(),
                distance, edition, expectedMapVersion, actionId);
    }

    public record ExplorationEnvironmentPhaseResponse(long adventureVersion,
            ExplorationCycleView cycle, String narration) {
        ExplorationEnvironmentPhaseResponse(long adventureVersion,
                com.dndmaster.adventure.domain.runtime.ExplorationCycle cycle, String narration) {
            this(adventureVersion, ExplorationCycleView.from(cycle), narration);
        }
    }

    @PutMapping("/api/v1/adventures/{adventureId}/exploration-groups")
    @org.springframework.transaction.annotation.Transactional
    ExplorationGroupsResponse saveExplorationGroups(@PathVariable UUID adventureId,
            @RequestBody ExplorationGroupsRequest request) {
        if (request == null || request.expectedVersion() == null || request.groups() == null || request.groups().isEmpty()
                || request.groups().stream().anyMatch(group -> group == null || group.id() == null || group.members() == null
                        || group.members().isEmpty() || group.members().stream().anyMatch(member -> member == null
                                || member.characterSheetId() == null || member.formation() == null)))
            throw new ApiRequestGuard.ApiContractException(400, "INVALID_EXPLORATION_GROUPS");
        Adventure adventure = adventureRepository.findByIdForUpdate(new AdventureId(adventureId)).orElseThrow();
        if (!adventure.ownerPlayerId().value().equals(playerResolver.playerId()))
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN);
        long started = com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.begin(LOGGER,
                "exploration_groups_save", "adventureId=" + adventureId + " groupCount=" + request.groups().size()
                        + " memberCount=" + request.groups().stream().mapToInt(group -> group.members().size()).sum()
                        + " expectedVersion=" + request.expectedVersion());
        if (adventure.version() != request.expectedVersion()) {
            var stale = new ApiRequestGuard.ApiContractException(409, "STALE_EXPLORATION_GROUPS");
            com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.fail(LOGGER,
                    "exploration_groups_save", "adventureId=" + adventureId + " expectedVersion=" + request.expectedVersion()
                            + " actualVersion=" + adventure.version(), started, stale);
            throw stale;
        }
        try {
            var proposed = request.groups().stream().map(group -> new com.dndmaster.adventure.domain.runtime.ExplorationGroup(
                    group.id(), UUID.randomUUID(), group.members().stream().map(member -> new com.dndmaster.adventure.domain.runtime.ExplorationGroup.Member(
                            member.characterSheetId(), member.formation(), characterCombatPort.movementSpeed(member.characterSheetId(),
                                    playerResolver.playerId(), adventure.sessionId().value()))).toList())).toList();
            var proposedState = new com.dndmaster.adventure.domain.runtime.ExplorationState(proposed);
            java.util.Set<UUID> partyIds = adventure.party().stream().map(member -> member.characterSheetId().value())
                    .collect(java.util.stream.Collectors.toSet());
            java.util.Set<UUID> requestedIds = proposedState.groups().stream().flatMap(group -> group.members().stream())
                    .map(com.dndmaster.adventure.domain.runtime.ExplorationGroup.Member::characterSheetId)
                    .collect(java.util.stream.Collectors.toSet());
            if (!partyIds.equals(requestedIds))
                throw new ApiRequestGuard.ApiContractException(400, "EXPLORATION_GROUP_MEMBERS_MUST_MATCH_PARTY");
            Object stored = adventure.gameState().values().get("explorationState");
            var previousState = stored == null ? initialExplorationState(adventure)
                    : objectMapper.convertValue(stored, com.dndmaster.adventure.domain.runtime.ExplorationState.class);
            var map = combatMapViewPort.playerView(adventureId, playerResolver.playerId())
                    .orElseThrow(() -> new ApiRequestGuard.ApiContractException(409, "EXPLORATION_MAP_UNAVAILABLE"));
            for (var nextGroup : proposedState.groups()) {
                var contributors = previousState.groups().stream().filter(previous -> previous.members().stream()
                        .anyMatch(old -> nextGroup.members().stream().anyMatch(current -> current.characterSheetId().equals(old.characterSheetId())))).toList();
                if (contributors.size() > 1) {
                    var positions = contributors.stream().map(previous -> map.tokens().stream()
                            .filter(token -> token.id().equals(previous.mapTokenId())).findFirst()
                            .orElseThrow(() -> new ApiRequestGuard.ApiContractException(409, "EXPLORATION_TOKEN_UNAVAILABLE")))
                            .map(token -> token.x() + "," + token.y()).collect(java.util.stream.Collectors.toSet());
                    if (positions.size() != 1) throw new ApiRequestGuard.ApiContractException(409, "EXPLORATION_GROUPS_NOT_COLOCATED");
                }
            }
            List<UUID> preferredTokenIds = proposedState.groups().stream().map(group -> previousState.groups().stream()
                    .filter(previous -> previous.id().equals(group.id())).map(com.dndmaster.adventure.domain.runtime.ExplorationGroup::mapTokenId)
                    .findFirst().orElse(null)).toList();
            UUID originTokenId = proposedState.groups().stream().filter(group -> previousState.groups().stream()
                            .noneMatch(previous -> previous.id().equals(group.id())))
                    .flatMap(group -> previousState.groups().stream().filter(previous -> previous.members().stream()
                            .map(com.dndmaster.adventure.domain.runtime.ExplorationGroup.Member::characterSheetId).collect(java.util.stream.Collectors.toSet())
                            .containsAll(group.members().stream().map(com.dndmaster.adventure.domain.runtime.ExplorationGroup.Member::characterSheetId)
                                    .collect(java.util.stream.Collectors.toSet()))).map(com.dndmaster.adventure.domain.runtime.ExplorationGroup::mapTokenId))
                    .findFirst().orElse(null);
            var tokens = combatMapViewPort.ensureExplorationTokens(map.mapId(), playerResolver.playerId(), map.version(),
                    UUID.randomUUID(), preferredTokenIds, originTokenId, proposedState.groups().size());
            var state = new com.dndmaster.adventure.domain.runtime.ExplorationState(
                    java.util.stream.IntStream.range(0, proposedState.groups().size()).mapToObj(index -> {
                        var group = proposedState.groups().get(index);
                        return new com.dndmaster.adventure.domain.runtime.ExplorationGroup(group.id(), tokens.get(index).id(), group.members());
                    }).toList());
            adventure.updateExplorationState(new OwnerPlayerId(playerResolver.playerId()), request.expectedVersion(), state);
            adventureRepository.save(adventure);
            com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.complete(LOGGER,
                    "exploration_groups_save", "adventureId=" + adventureId + " expectedVersion=" + request.expectedVersion()
                            + " actualVersion=" + adventure.version() + " groupCount=" + state.groups().size(), started);
            return new ExplorationGroupsResponse(adventure.version(), state.groups());
        } catch (RuntimeException | Error failure) {
            com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.fail(LOGGER,
                    "exploration_groups_save", "adventureId=" + adventureId + " groupCount=" + request.groups().size()
                            + " memberCount=" + request.groups().stream().mapToInt(group -> group.members().size()).sum()
                            + " expectedVersion=" + request.expectedVersion() + " actualVersion=" + adventure.version(), started, failure);
            throw failure;
        }
    }

    private Adventure ownedAdventure(UUID adventureId) {
        Adventure adventure = adventureRepository.findById(new AdventureId(adventureId)).orElseThrow();
        if (!adventure.ownerPlayerId().value().equals(playerResolver.playerId()))
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN);
        return adventure;
    }

    record ExplorationMemberInput(UUID characterSheetId, com.dndmaster.adventure.domain.runtime.ExplorationGroup.Formation formation) { }
    record ExplorationGroupInput(UUID id, List<ExplorationMemberInput> members) { }
    record ExplorationGroupsRequest(Long expectedVersion, List<ExplorationGroupInput> groups) { }
    record ExplorationGroupsResponse(long version,
            List<com.dndmaster.adventure.domain.runtime.ExplorationGroup> groups,
            ExplorationCycleView cycle) {
        ExplorationGroupsResponse(long version, List<com.dndmaster.adventure.domain.runtime.ExplorationGroup> groups) {
            this(version, groups, (ExplorationCycleView) null);
        }
        ExplorationGroupsResponse(long version, List<com.dndmaster.adventure.domain.runtime.ExplorationGroup> groups,
                com.dndmaster.adventure.domain.runtime.ExplorationCycle cycle) {
            this(version, groups, ExplorationCycleView.from(cycle));
        }
    }
    record ExplorationCycleView(UUID id, long cycleNumber, List<UUID> groupOrder, java.util.Set<UUID> completedGroups,
            com.dndmaster.adventure.domain.runtime.ExplorationCycle.Phase phase,
            com.dndmaster.adventure.domain.runtime.ExplorationCycle.EnvironmentPhase environmentPhase,
            UUID currentGroupId, UUID currentPhaseCommandId, long elapsedSeconds,
            PendingEnvironmentProposalView pendingEnvironmentProposal, MovementStopEventView pendingMovementEvent,
            boolean combatLinked) {
        static ExplorationCycleView from(com.dndmaster.adventure.domain.runtime.ExplorationCycle cycle) {
            if (cycle == null) return null;
            var proposal = cycle.pendingEnvironmentProposal();
            var event = cycle.pendingMovementEvent();
            return new ExplorationCycleView(cycle.id(), cycle.cycleNumber(), cycle.groupOrder(), cycle.completedGroups(),
                    cycle.phase(), cycle.environmentPhase(), cycle.currentGroupId(), cycle.currentPhaseCommandId(),
                    cycle.elapsedSeconds(), proposal == null ? null : new PendingEnvironmentProposalView(
                            proposal.narration(), proposal.encounterRequested(), proposal.linkedCombatEncounterId() != null),
                    event == null ? null : new MovementStopEventView(event.id(), event.groupId(),
                            publicMovementEventLabel(event.cause()), publicMovementEventLabels(event.publicEvents()),
                            event.finalPosition()), cycle.linkedCombatEncounterId() != null);
        }

        private static String publicMovementEventLabel(String label) {
            return switch (label == null ? "" : label) {
                case "HOSTILE_OBSERVED", "SPATIAL_FEATURE_REQUIRES_DECISION" -> label;
                default -> "MOVEMENT_INTERRUPTED";
            };
        }

        private static List<String> publicMovementEventLabels(List<String> labels) {
            if (labels == null) return List.of();
            return labels.stream().map(ExplorationCycleView::publicMovementEventLabel).distinct().toList();
        }
    }
    record PendingEnvironmentProposalView(String narration, boolean encounterRequested, boolean combatLinked) { }
    record MovementStopEventView(UUID id, UUID groupId, String cause, List<String> publicEvents,
            com.dndmaster.adventure.domain.runtime.ExplorationCycle.Position finalPosition) { }
    record ExplorationCycleStartRequest(Long expectedVersion, UUID commandId) { }
    record ExplorationCycleFinishRequest(Long expectedVersion, UUID commandId, Boolean interrupted) { }
    record ExplorationMovementEventResolveRequest(Long expectedVersion) { }
    record ExplorationThreatNotificationRequest(Long expectedVersion, UUID commandId, UUID groupId, UUID discovererCharacterSheetId) { }

    @PostMapping("/api/v1/adventures/{adventureId}/exploration/notify-threat")
    ExplorationGroupsResponse notifyExplorationThreat(@PathVariable UUID adventureId,
            @RequestBody ExplorationThreatNotificationRequest request) {
        if (request == null || request.expectedVersion() == null || request.commandId() == null
                || request.groupId() == null || request.discovererCharacterSheetId() == null)
            throw new ApiRequestGuard.ApiContractException(400, "INVALID_EXPLORATION_THREAT_NOTIFICATION");
        Adventure adventure = adventureRepository.findByIdForUpdate(new AdventureId(adventureId)).orElseThrow();
        UUID owner = playerResolver.playerId();
        if (!adventure.ownerPlayerId().value().equals(owner))
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN);
        var state = storedExplorationState(adventure);
        if (state.cycle() == null) throw new ApiRequestGuard.ApiContractException(409, "EXPLORATION_CYCLE_NOT_STARTED");
        var group = state.groups().stream().filter(candidate -> candidate.id().equals(request.groupId())).findFirst()
                .orElseThrow(() -> new ApiRequestGuard.ApiContractException(400, "EXPLORATION_GROUP_NOT_FOUND"));
        var members = group.members().stream().map(com.dndmaster.adventure.domain.runtime.ExplorationGroup.Member::characterSheetId)
                .collect(java.util.stream.Collectors.toSet());
        long started = com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.begin(LOGGER,
                "exploration_threat_notification", "adventureId=" + adventureId + " commandId=" + request.commandId()
                        + " groupId=" + group.id() + " memberCount=" + members.size());
        try {
            var pendingEvent = state.cycle().pendingMovementEvent();
            var pendingProposal = state.cycle().pendingEnvironmentProposal();
            boolean validDiscoverer = members.contains(request.discovererCharacterSheetId())
                    && ((pendingEvent != null && pendingEvent.groupId().equals(group.id())
                            && pendingEvent.discoveredByCharacterSheetIds().contains(request.discovererCharacterSheetId()))
                    || (pendingProposal != null && pendingProposal.threatDiscovery() != null
                            && pendingProposal.threatDiscovery().discovererCharacterSheetIds().contains(request.discovererCharacterSheetId())));
            if (!validDiscoverer) throw new ApiRequestGuard.ApiContractException(400, "CHARACTER_HAS_NOT_DISCOVERED_THREAT");
            boolean alreadyShared = (pendingEvent != null && pendingEvent.groupId().equals(group.id())
                    && pendingEvent.awareCharacterSheetIds().containsAll(members))
                    || (pendingProposal != null && pendingProposal.awareCharacterSheetIds().containsAll(members));
            if (alreadyShared) {
                com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.complete(LOGGER,
                        "exploration_threat_notification", "adventureId=" + adventureId + " commandId=" + request.commandId()
                                + " expectedVersion=" + request.expectedVersion() + " actualVersion=" + adventure.version()
                                + " memberCount=" + members.size() + " result=already_shared", started);
                return new ExplorationGroupsResponse(adventure.version(), state.groups(), state.cycle());
            }
            var map = combatMapViewPort.playerView(adventureId, owner)
                    .orElseThrow(() -> new ApiRequestGuard.ApiContractException(409, "EXPLORATION_MAP_UNAVAILABLE"));
            String mapState = combatMapViewPort.gmEnvironmentView(map.mapId(), owner);
            var groupFacts = state.groups().stream().map(item -> Map.of("groupId", item.id(), "members", item.members().stream()
                    .map(member -> Map.of("characterSheetId", member.characterSheetId(), "formation", member.formation(), "speed", member.speed())).toList())).toList();
            int mapStateOffset = mapState.indexOf(" GM_MAP_STATE=");
            var eligibleEnemyTokenIds = hostileTokenIds(mapState);
            String notificationInput = "EXPLICIT_PARTY_NOTIFICATION=the discoverer explicitly tells this group about the discovered threat; judge only whether nearby enemies hear the notification.\n"
                    + "NOTIFYING_CHARACTER=" + request.discovererCharacterSheetId() + "\nNOTIFYING_GROUP=" + objectMapper.valueToTree(groupFacts)
                    + "\nELIGIBLE_HOSTILE_TOKEN_IDS=" + objectMapper.valueToTree(eligibleEnemyTokenIds)
                    + (mapStateOffset < 0 ? "\n" + mapState : "\n" + mapState.substring(0, mapStateOffset) + mapState.substring(mapStateOffset));
            var audibility = runtimeTurnService.planExplorationEnvironmentPhase(new AdventureId(adventureId),
                    new OwnerPlayerId(owner), request.commandId(), state.cycle().environmentPhase(), notificationInput).threatDiscovery();
            if (audibility != null && (!eligibleEnemyTokenIds.containsAll(audibility.awareEnemyTokenIds())
                    || audibility.enemyHeardNotification() != !audibility.awareEnemyTokenIds().isEmpty())) {
                if (com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.enabled())
                    LOGGER.info("dev_exploration_threat_notification outcome=failed adventureId={} commandId={} expectedHostileCount={} actualAwareCount={} failure=unknown_hostile_id recovery=retry_same_command",
                            adventureId, request.commandId(), eligibleEnemyTokenIds.size(), audibility.awareEnemyTokenIds().size());
                throw new ApiRequestGuard.ApiContractException(502, "UNKNOWN_AWARE_ENEMY_TOKEN_ID");
            }
            boolean enemiesHeard = audibility != null && audibility.enemyHeardNotification();
            if (adventure.version() != request.expectedVersion())
                throw new ApiRequestGuard.ApiContractException(409, "STALE_EXPLORATION_THREAT_NOTIFICATION");
            var cycle = state.cycle().shareThreatWithGroup(group.id(), request.discovererCharacterSheetId(), members, enemiesHeard,
                    audibility == null ? java.util.Set.of() : java.util.Set.copyOf(audibility.awareEnemyTokenIds()));
            if (cycle == state.cycle()) {
                com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.complete(LOGGER,
                        "exploration_threat_notification", "adventureId=" + adventureId + " commandId=" + request.commandId()
                                + " expectedVersion=" + request.expectedVersion() + " actualVersion=" + adventure.version()
                                + " memberCount=" + members.size() + " enemiesHeard=" + enemiesHeard
                                + " awareEnemyCount=" + (audibility == null ? 0 : audibility.awareEnemyTokenIds().size())
                                + " result=already_shared", started);
                return new ExplorationGroupsResponse(adventure.version(), state.groups(), cycle);
            }
            if (adventure.version() != request.expectedVersion())
                throw new ApiRequestGuard.ApiContractException(409, "STALE_EXPLORATION_THREAT_NOTIFICATION");
            adventure.updateExplorationState(new OwnerPlayerId(owner), adventure.version(), state.withCycle(cycle));
            adventureRepository.save(adventure);
            com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.complete(LOGGER,
                    "exploration_threat_notification", "adventureId=" + adventureId + " commandId=" + request.commandId()
                            + " expectedVersion=" + request.expectedVersion() + " memberCount=" + members.size()
                            + " enemiesHeard=" + enemiesHeard + " awareEnemyCount=" + (audibility == null ? 0 : audibility.awareEnemyTokenIds().size())
                            + " result=shared", started);
            return new ExplorationGroupsResponse(adventure.version(), state.groups(), cycle);
        } catch (RuntimeException | Error failure) {
            com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.fail(LOGGER,
                    "exploration_threat_notification", "adventureId=" + adventureId + " commandId=" + request.commandId()
                            + " expectedVersion=" + request.expectedVersion() + " memberCount=" + members.size()
                            + " recoveryAction=retry_same_command", started, failure);
            throw failure;
        }
    }

    @PostMapping("/api/v1/adventures/{adventureId}/combat-map/movement-preview")
    CombatMapMovementPreviewResponse previewMovement(@PathVariable UUID adventureId,
            @RequestBody CombatMapMovementPreviewRequest request) {
        Adventure adventure = adventureRepository.findById(new AdventureId(adventureId)).orElseThrow();
        UUID owner = playerResolver.playerId();
        if (!adventure.ownerPlayerId().value().equals(owner)) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN);
        }
        if (request == null || request.mapId() == null || request.tokenId() == null || request.destination() == null
                || request.mapVersion() == null || request.mapVersion() < 0 || invalidPreviewPosition(request.destination())
                || request.waypoints() != null && (request.waypoints().size() > CombatMapPreviewCommand.MAX_WAYPOINTS
                        || request.waypoints().stream().anyMatch(AdventureController::invalidPreviewPosition))) {
            throw new ApiRequestGuard.ApiContractException(400, "INVALID_MOVEMENT_PREVIEW");
        }
        var playerMap = combatMapViewPort.playerView(adventureId, owner);
        if (playerMap.isEmpty() || !request.mapId().equals(playerMap.get().mapId())) {
            throw new ApiRequestGuard.ApiContractException(400, "INVALID_MOVEMENT_PREVIEW");
        }
        requireActiveExplorationGroup(adventure, request.mapId(), request.tokenId());
        CombatMapPreviewResult preview = combatMapPort.preview(new CombatMapPreviewCommand(
                request.mapId(), owner, request.tokenId(), toPreviewPosition(request.destination()),
                request.waypoints() == null ? List.of() : request.waypoints().stream().map(AdventureController::toPreviewPosition).toList(),
                appliedEdition(adventure).edition(), request.mapVersion()));
        requireExplorationMovementBudget(adventure, request.tokenId(), preview.distance());
        pendingMapMovementConfirmationRepository.save(new PendingMapMovementConfirmation(
                adventureId, owner, preview.mapId(), request.tokenId(), preview.baseMapVersion(),
                preview.orderedPositions().stream()
                        .map(position -> new PendingMapMovementConfirmation.Position(position.x(), position.y())).toList(),
                preview.distance(), preview.fingerprint(), request.waypoints() == null ? List.of() : request.waypoints().stream()
                        .map(position -> new PendingMapMovementConfirmation.Position(position.x(), position.y())).toList(),
                request.sourceText(), new PendingMapMovementConfirmation.Position(request.destination().x(), request.destination().y()),
                request.pendingTurnId(), null, false));
        return CombatMapMovementPreviewResponse.from(preview);
    }

    @PostMapping("/api/v1/adventures/{adventureId}/map-movement/preview")
    NaturalLanguageMovementPreviewResponse previewNaturalLanguageMovement(@PathVariable UUID adventureId,
            @RequestBody NaturalLanguageMovementPreviewRequest request) {
        Adventure adventure = adventureRepository.findById(new AdventureId(adventureId)).orElseThrow();
        UUID owner = playerResolver.playerId();
        if (!adventure.ownerPlayerId().value().equals(owner)) throw new ApiRequestGuard.ApiContractException(403, "OWNERSHIP_DENIED");
        if (request == null || request.sourceText() == null || request.sourceText().isBlank() || request.mapId() == null
                || request.mapVersion() == null || request.mapVersion() < 0 || request.tokenId() == null) {
            throw new ApiRequestGuard.ApiContractException(400, "INVALID_MOVEMENT_PLACEMENT");
        }
        var view = combatMapViewPort.playerView(adventureId, owner)
                .filter(item -> item.mapId().equals(request.mapId()))
                .orElseThrow(() -> new ApiRequestGuard.ApiContractException(400, "INVALID_MOVEMENT_PLACEMENT"));
        requireActiveExplorationGroup(adventure, request.mapId(), request.tokenId());
        try {
            characterForMovementToken(adventure, owner, request.tokenId());
        } catch (IllegalArgumentException | IllegalStateException exception) {
            throw new ApiRequestGuard.ApiContractException(400, "INVALID_MOVEMENT_PLACEMENT");
        }
        var proposal = movementPlacementModelPort.interpret(new MovementPlacementModelPort.MovementPlacementContext(
                request.sourceText(), publicMovementMap(view), currentPosition(view, request.tokenId()), request.tacticalContext()));
        if (!"RESOLVED".equals(proposal.status())) return NaturalLanguageMovementPreviewResponse.from(proposal, null, null, null);
        var existing = pendingMapMovementConfirmationRepository.findByAdventureId(adventureId, owner)
                .filter(item -> !item.terminal() && item.mapId().equals(request.mapId()) && item.tokenId().equals(request.tokenId())
                        && item.mapVersion() == request.mapVersion() && item.sourceText().equals(request.sourceText().trim()));
        if (existing.isPresent()) {
            var saved = existing.get();
            var savedDestination = saved.destination();
            var savedProposal = new MovementPlacementModelPort.MovementPlacementProposal("RESOLVED",
                    savedDestination == null ? null : new MovementPlacementModelPort.Position(savedDestination.x(), savedDestination.y()),
                    List.of(), "저장된 이동 확인을 복원했습니다.");
            return NaturalLanguageMovementPreviewResponse.from(savedProposal, new CombatMapPreviewResult(saved.mapId(),
                    saved.path().stream().map(position -> new CombatMapPreviewPosition(position.x(), position.y())).toList(),
                    saved.distance(), saved.mapVersion(), saved.fingerprint()), saved.pendingTurnId(), saved.confirmationCommandId());
        }
        var destination = proposal.destination();
        CombatMapPreviewResult preview = combatMapPort.preview(new CombatMapPreviewCommand(request.mapId(), owner, request.tokenId(),
                new CombatMapPreviewPosition(destination.x(), destination.y()), List.of(), appliedEdition(adventure).edition(), request.mapVersion()));
        requireExplorationMovementBudget(adventure, request.tokenId(), preview.distance());
        UUID pendingTurnId = currentRuntimeTurnId(adventure);
        UUID confirmationCommandId = UUID.randomUUID();
        pendingMapMovementConfirmationRepository.save(new PendingMapMovementConfirmation(adventureId, owner, preview.mapId(), request.tokenId(),
                preview.baseMapVersion(), preview.orderedPositions().stream()
                        .map(position -> new PendingMapMovementConfirmation.Position(position.x(), position.y())).toList(), preview.distance(), preview.fingerprint(), List.of(), request.sourceText(),
                new PendingMapMovementConfirmation.Position(destination.x(), destination.y()), pendingTurnId, confirmationCommandId, false));
        return NaturalLanguageMovementPreviewResponse.from(proposal, preview, pendingTurnId, confirmationCommandId);
    }

    @PostMapping("/api/v1/adventures/{adventureId}/map-movement/confirm")
    @org.springframework.transaction.annotation.Transactional
    NaturalLanguageMovementConfirmationResponse confirmNaturalLanguageMovement(@PathVariable UUID adventureId,
            @RequestBody NaturalLanguageMovementConfirmationRequest request) {
        Adventure adventure = adventureRepository.findByIdForUpdate(new AdventureId(adventureId)).orElseThrow();
        UUID owner = playerResolver.playerId();
        if (!adventure.ownerPlayerId().value().equals(owner)) throw new ApiRequestGuard.ApiContractException(403, "OWNERSHIP_DENIED");
        PendingMapMovementConfirmation pending = pendingMapMovementConfirmationRepository.findByAdventureId(adventureId, owner)
                .orElseThrow(() -> new ApiRequestGuard.ApiContractException(409, "MOVEMENT_CONFIRMATION_NOT_FOUND"));
        if (request == null || request.commandId() == null || request.pendingTurnId() == null || request.tokenId() == null
                || !request.pendingTurnId().equals(pending.pendingTurnId()) || request.mapVersion() == null
                || request.mapVersion() != pending.mapVersion() || !request.tokenId().equals(pending.tokenId())) {
            throw new ApiRequestGuard.ApiContractException(409, "STALE_MOVEMENT_PROPOSAL");
        }
        if (pending.confirmationCommandId() != null && !request.commandId().equals(pending.confirmationCommandId())) {
            throw new ApiRequestGuard.ApiContractException(409, "STALE_MOVEMENT_PROPOSAL");
        }
        if (pending.terminal()) {
            CombatMapMoveResult savedResult = readTerminalMovementResult(pending);
            persistMovementStopEvent(adventure, pending.mapId(), pending.tokenId(), savedResult);
            return NaturalLanguageMovementConfirmationResponse.from(savedResult);
        }
        requireActiveExplorationGroup(adventure, pending.mapId(), pending.tokenId());
        validatePendingRuntimeTurn(adventure, pending.pendingTurnId());
        var destination = pending.destination() == null ? pending.path().getLast() : pending.destination();
        CombatMapPreviewResult preview = null;
        if (!pending.terminal()) {
            preview = combatMapPort.preview(new CombatMapPreviewCommand(pending.mapId(), owner, pending.tokenId(),
                    new CombatMapPreviewPosition(destination.x(), destination.y()), pending.waypoints().stream()
                            .map(point -> new CombatMapPreviewPosition(point.x(), point.y())).toList(), appliedEdition(adventure).edition(), pending.mapVersion()));
            if (!pending.fingerprint().equals(preview.fingerprint())
                    || !pending.path().equals(preview.orderedPositions().stream()
                            .map(position -> new PendingMapMovementConfirmation.Position(position.x(), position.y())).toList())) {
                throw new ApiRequestGuard.ApiContractException(409, "STALE_MOVEMENT_PROPOSAL");
            }
        }
        var member = characterForMovementToken(adventure, owner, pending.tokenId());
        List<PendingMapMovementConfirmation.Position> requestedPath = pending.terminal() ? pending.path()
                : preview.orderedPositions().stream().map(position -> new PendingMapMovementConfirmation.Position(position.x(), position.y())).toList();
        int distance = pending.terminal() ? pending.distance() : preview.distance();
        String fingerprint = pending.terminal() ? pending.fingerprint() : preview.fingerprint();
        String path = requestedPath.stream().map(position -> position.x() + "," + position.y()).reduce((left, right) -> left + ";" + right).orElseThrow();
        CombatActionCommand command = new CombatActionCommand(request.commandId(), adventure.id(), adventure.sessionId().value(), adventure.ruleSetId(),
                member.characterSheetId(), pending.mapId(), CombatActorRole.PLAYER, "MOVE", path, owner, pending.tokenId(), pending.mapVersion());
        characterCombatPort.requireUsableCharacter(command);
        CombatMapMoveResult result = mapMovementCoordinator.resolve(new CombatMapMoveCommand(command, distance, pending.mapVersion(),
                appliedEdition(adventure).edition(), fingerprint, pending.waypoints().stream()
                        .map(point -> new CombatMapPreviewPosition(point.x(), point.y())).toList()));
        recordExplorationMovementSpend(adventure, pending.tokenId(), result);
        boolean terminal = result.status() == CombatMapMovementStatus.COMMITTED
                || result.status() == CombatMapMovementStatus.INTERRUPTED
                || result.status() == CombatMapMovementStatus.CANCELLED;
        pendingMapMovementConfirmationRepository.save(new PendingMapMovementConfirmation(adventureId, owner, pending.mapId(), pending.tokenId(),
                pending.mapVersion(), pending.path(), pending.distance(), pending.fingerprint(), pending.waypoints(), pending.sourceText(),
                pending.destination(), pending.pendingTurnId(), pending.confirmationCommandId() == null ? request.commandId() : pending.confirmationCommandId(), terminal,
                terminal ? writeTerminalMovementResult(result) : null));
        persistMovementStopEvent(adventure, pending.mapId(), pending.tokenId(), result);
        return NaturalLanguageMovementConfirmationResponse.from(result);
    }

    @GetMapping("/api/v1/adventures/{adventureId}/map-movement/pending")
    ResponseEntity<PendingMapMovementResponse> pendingMapMovement(@PathVariable UUID adventureId) {
        Adventure adventure = adventureRepository.findById(new AdventureId(adventureId)).orElseThrow();
        UUID owner = playerResolver.playerId();
        if (!adventure.ownerPlayerId().value().equals(owner)) {
            throw new ApiRequestGuard.ApiContractException(403, "OWNERSHIP_DENIED");
        }
        return pendingMapMovementConfirmationRepository.findByAdventureId(adventureId, owner)
                .filter(pending -> pending.pendingTurnId() != null)
                .map(pending -> ResponseEntity.ok(PendingMapMovementResponse.from(pending)))
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @DeleteMapping("/api/v1/adventures/{adventureId}/map-movement/pending")
    ResponseEntity<Void> clearPendingMapMovement(@PathVariable UUID adventureId) {
        Adventure adventure = adventureRepository.findById(new AdventureId(adventureId)).orElseThrow();
        UUID owner = playerResolver.playerId();
        if (!adventure.ownerPlayerId().value().equals(owner)) {
            throw new ApiRequestGuard.ApiContractException(403, "OWNERSHIP_DENIED");
        }
        pendingMapMovementConfirmationRepository.deleteByAdventureId(adventureId, owner);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/v1/adventures/{adventureId}/combat-map/movement-operations/{operationId}")
    @org.springframework.transaction.annotation.Transactional
    AdventureMovementOperationResponse movementOperation(@PathVariable UUID adventureId, @PathVariable UUID operationId,
            @RequestParam UUID mapId) { return recoveryMovement(adventureId, mapId, operationId, "query"); }

    @PostMapping("/api/v1/adventures/{adventureId}/combat-map/movement-operations/{operationId}/resume")
    @org.springframework.transaction.annotation.Transactional
    AdventureMovementOperationResponse resumeMovementOperation(@PathVariable UUID adventureId, @PathVariable UUID operationId,
            @RequestParam UUID mapId, @RequestHeader("Idempotency-Key") UUID commandId,
            @RequestBody(required = false) com.dndmaster.adventure.application.combat.CombatMapCheckSubmission submission) {
        return recoveryMovement(adventureId, mapId, operationId, "resume", submission, commandId);
    }

    @PostMapping("/api/v1/adventures/{adventureId}/combat-map/movement-operations/{operationId}/roll")
    @org.springframework.transaction.annotation.Transactional
    AdventureMovementOperationResponse rollMovementCheck(@PathVariable UUID adventureId, @PathVariable UUID operationId,
            @RequestHeader("Idempotency-Key") UUID idempotencyKey,
            @RequestBody SpatialCheckRollRequest request) {
        Adventure adventure = adventureRepository.findById(new AdventureId(adventureId)).orElseThrow();
        UUID owner = playerResolver.playerId();
        if (!adventure.ownerPlayerId().value().equals(owner)) {
            throw new ApiRequestGuard.ApiContractException(403, "OWNERSHIP_DENIED");
        }
        if (request == null || request.mapId() == null || request.operationId() == null
                || !operationId.equals(request.operationId()) || request.checkId() == null
                || request.ownerPlayerId() == null || !owner.equals(request.ownerPlayerId())
                || request.commandId() == null) {
            throw new ApiRequestGuard.ApiContractException(400, "INVALID_SPATIAL_CHECK_ROLL");
        }
        requireSpatialIdempotencyKey(idempotencyKey, request);
        if (combatMapViewPort.playerView(adventureId, owner).filter(view -> request.mapId().equals(view.mapId())).isEmpty()) {
            throw new ApiRequestGuard.ApiContractException(403, "OWNERSHIP_DENIED");
        }
        var result = mapMovementCoordinator.rollAndResume(new com.dndmaster.adventure.application.combat.SpatialCheckRollCommand(
                adventure.id().value(), request.mapId(), adventure.sessionId().value(), adventure.ruleSetId(), owner,
                request.checkId(), operationId, request.commandId(), request.expectedVersion()));
        persistMovementStopEvent(adventure, request.mapId(), result, true);
        return AdventureMovementOperationResponse.from(result);
    }

    @DeleteMapping("/api/v1/adventures/{adventureId}/combat-map/movement-operations/{operationId}")
    @org.springframework.transaction.annotation.Transactional
    AdventureMovementOperationResponse cancelMovementOperation(@PathVariable UUID adventureId, @PathVariable UUID operationId,
            @RequestParam UUID mapId, @RequestHeader("Idempotency-Key") UUID commandId) {
        return recoveryMovement(adventureId, mapId, operationId, "cancel", null, commandId);
    }

    @GetMapping("/api/v1/adventures/{adventureId}/combat-map/movement-operations")
    ResponseEntity<AdventureMovementOperationResponse> latestMovementOperation(@PathVariable UUID adventureId,
            @RequestParam UUID mapId) {
        Adventure adventure = adventureRepository.findById(new AdventureId(adventureId)).orElseThrow();
        UUID owner = playerResolver.playerId();
        if (!adventure.ownerPlayerId().value().equals(owner)
                || combatMapViewPort.playerView(adventureId, owner).filter(view -> mapId.equals(view.mapId())).isEmpty()) {
            throw new ApiRequestGuard.ApiContractException(403, "OWNERSHIP_DENIED");
        }
        var result = mapMovementCoordinator.latest(mapId);
        return result == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(AdventureMovementOperationResponse.from(result));
    }

    private AdventureMovementOperationResponse recoveryMovement(UUID adventureId, UUID mapId, UUID operationId, String action) {
        return recoveryMovement(adventureId, mapId, operationId, action, null, null);
    }

    private AdventureMovementOperationResponse recoveryMovement(UUID adventureId, UUID mapId, UUID operationId, String action,
            com.dndmaster.adventure.application.combat.CombatMapCheckSubmission submission) {
        return recoveryMovement(adventureId, mapId, operationId, action, submission, null);
    }

    private AdventureMovementOperationResponse recoveryMovement(UUID adventureId, UUID mapId, UUID operationId, String action,
            com.dndmaster.adventure.application.combat.CombatMapCheckSubmission submission, UUID commandId) {
        Adventure adventure = adventureRepository.findById(new AdventureId(adventureId)).orElseThrow();
        UUID owner = playerResolver.playerId();
        if (!adventure.ownerPlayerId().value().equals(owner)
                || combatMapViewPort.playerView(adventureId, owner).filter(view -> mapId.equals(view.mapId())).isEmpty()) {
            throw new ApiRequestGuard.ApiContractException(403, "OWNERSHIP_DENIED");
        }
        if (submission != null && !owner.equals(submission.ownerPlayerId())) {
            throw new ApiRequestGuard.ApiContractException(403, "CHECK_OWNERSHIP_DENIED");
        }
        if (submission != null && submission.actor() != com.dndmaster.adventure.application.combat.CombatMapCheckActor.PLAYER) {
            throw new ApiRequestGuard.ApiContractException(400, "INVALID_SPATIAL_CHECK_ACTOR");
        }
        if ("resume".equals(action)) {
            if (commandId == null || (submission == null ? !operationId.equals(commandId) : !commandId.equals(submission.commandId()))) {
                throw new ApiRequestGuard.ApiContractException(400, "IDEMPOTENCY_KEY_MISMATCH");
            }
        }
        if ("cancel".equals(action)) requireCancelCommandId(commandId);
        var result = switch (action) { case "resume" -> submission == null ? mapMovementCoordinator.resume(mapId, operationId) : mapMovementCoordinator.resume(mapId, operationId, submission); case "cancel" -> mapMovementCoordinator.cancel(mapId, operationId, commandId); default -> mapMovementCoordinator.query(mapId, operationId); };
        persistMovementStopEvent(adventure, mapId, result, true);
        return AdventureMovementOperationResponse.from(result);
    }

    private void requireActiveExplorationGroup(Adventure adventure, UUID mapId, UUID tokenId) {
        if (adventure.gameState() == null) return;
        var state = storedExplorationState(adventure);
        if (state.cycle() == null) return;
        var group = state.groups().stream().filter(candidate -> candidate.mapTokenId().equals(tokenId)).findFirst()
                .orElseThrow(() -> new ApiRequestGuard.ApiContractException(409, "MOVEMENT_TOKEN_NOT_IN_EXPLORATION_GROUP"));
        if (!group.id().equals(state.cycle().currentGroupId()))
            throw new ApiRequestGuard.ApiContractException(409, "EXPLORATION_GROUP_TURN_NOT_ACTIVE");
        if (combatMapViewPort.playerView(adventure.id().value(), adventure.ownerPlayerId().value())
                .filter(view -> view.mapId().equals(mapId)
                        && view.tokens().stream().anyMatch(token -> token.id().equals(tokenId) && "PLAYER".equals(token.type())))
                .isEmpty())
            throw new ApiRequestGuard.ApiContractException(403, "OWNERSHIP_DENIED");
    }

    private void requireExplorationMovementBudget(Adventure adventure, UUID tokenId, int requestedDistance) {
        if (adventure.gameState() == null || requestedDistance <= 0) return;
        var state = storedExplorationState(adventure);
        if (state.cycle() == null) return;
        var group = state.groups().stream().filter(candidate -> candidate.mapTokenId().equals(tokenId)).findFirst()
                .orElseThrow(() -> new ApiRequestGuard.ApiContractException(409, "MOVEMENT_TOKEN_NOT_IN_EXPLORATION_GROUP"));
        if (!group.id().equals(state.cycle().currentGroupId()))
            throw new ApiRequestGuard.ApiContractException(409, "EXPLORATION_GROUP_TURN_NOT_ACTIVE");
        long started = com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.begin(LOGGER,
                "exploration_movement_budget", "adventureId=" + adventure.id().value() + " cycleId=" + state.cycle().id()
                        + " cycleNumber=" + state.cycle().cycleNumber() + " groupId=" + group.id()
                        + " requestedDistance=" + requestedDistance + " speed=" + group.speed());
        int spent = 0;
        int remaining = group.speed();
        try {
            spent = explorationMovementSpent(adventure, state.cycle().id(), state.cycle().cycleNumber(), group.id());
            remaining = Math.max(0, group.speed() - spent);
            if (requestedDistance > remaining)
                throw new ApiRequestGuard.ApiContractException(409, "EXPLORATION_MOVEMENT_BUDGET_EXCEEDED");
            com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.complete(LOGGER,
                    "exploration_movement_budget", "adventureId=" + adventure.id().value() + " cycleId=" + state.cycle().id()
                            + " cycleNumber=" + state.cycle().cycleNumber() + " groupId=" + group.id()
                            + " requestedDistance=" + requestedDistance + " spent=" + spent + " remaining=" + remaining
                            + " expected=" + group.speed() + " result=accepted", started);
        } catch (RuntimeException | Error failure) {
            com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.fail(LOGGER,
                    "exploration_movement_budget", "adventureId=" + adventure.id().value() + " cycleId=" + state.cycle().id()
                            + " cycleNumber=" + state.cycle().cycleNumber() + " groupId=" + group.id()
                            + " requestedDistance=" + requestedDistance + " spent=" + spent + " remaining=" + remaining
                            + " expected=" + group.speed()
                            + " result=rejected recoveryAction=choose_path_within_remaining_budget", started, failure);
            throw failure;
        }
    }

    private int explorationMovementSpent(Adventure adventure, UUID cycleId, long cycleNumber, UUID groupId) {
        int spent = 0;
        for (var event : sessionEventRepository.after(adventure.sessionId().value(), -1)) {
            if (!"EXPLORATION_MOVEMENT_SPENT".equals(event.type())) continue;
            try {
                JsonNode payload = objectMapper.readTree(event.payload());
                if (cycleId.toString().equals(payload.path("cycleId").asText())
                        && cycleNumber == payload.path("cycleNumber").asLong(-1)
                        && groupId.toString().equals(payload.path("groupId").asText())) {
                    int distance = payload.path("distance").asInt(-1);
                    if (distance <= 0) throw new IllegalStateException("invalid exploration movement spend distance");
                    spent = Math.addExact(spent, distance);
                }
            } catch (java.io.IOException | ArithmeticException failure) {
                throw new IllegalStateException("could not read exploration movement spends", failure);
            }
        }
        return spent;
    }

    private void recordExplorationMovementSpend(UUID adventureId,
            com.dndmaster.adventure.domain.runtime.GmInput.MapActionInput input, CombatMapMoveResult result) {
        if (input == null || result == null) return;
        try {
            MapActionPayload payload = objectMapper.readValue(input.action(), MapActionPayload.class);
            recordExplorationMovementSpend(adventureRepository.findById(new AdventureId(adventureId)).orElseThrow(),
                    payload.tokenId(), result);
        } catch (java.io.IOException failure) {
            throw new IllegalStateException("could not read committed map movement input", failure);
        }
    }

    private void recordExplorationMovementSpend(Adventure adventure, UUID tokenId, CombatMapMoveResult result) {
        if (result.operationId() == null || result.status() != CombatMapMovementStatus.COMMITTED) return;
        var state = storedExplorationState(adventure);
        if (state.cycle() == null) return;
        var group = state.groups().stream().filter(candidate -> candidate.mapTokenId().equals(tokenId)).findFirst().orElse(null);
        if (group == null || !group.id().equals(state.cycle().currentGroupId())) return;
        var view = combatMapViewPort.playerView(adventure.id().value(), adventure.ownerPlayerId().value()).orElseThrow();
        var path = result.traversedPath().size() > 1 ? result.traversedPath() : result.requestedPath();
        int distance = Math.max(0, path.size() - 1) * view.grid().distanceUnit();
        if (distance <= 0) return;
        UUID eventId = UUID.nameUUIDFromBytes((state.cycle().id() + ":movement-spend:" + result.operationId())
                .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        var eventPayload = objectMapper.createObjectNode().put("cycleId", state.cycle().id().toString())
                .put("cycleNumber", state.cycle().cycleNumber()).put("groupId", group.id().toString())
                .put("operationId", result.operationId().toString()).put("distance", distance);
        long started = com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.begin(LOGGER,
                "exploration_movement_spend", "adventureId=" + adventure.id().value() + " cycleId=" + state.cycle().id()
                        + " cycleNumber=" + state.cycle().cycleNumber() + " groupId=" + group.id()
                        + " operationId=" + result.operationId() + " pathCells=" + path.size() + " distance=" + distance);
        try {
            sessionEventRepository.appendNext(adventure.sessionId().value(), eventId,
                    "EXPLORATION_MOVEMENT_SPENT", objectMapper.writeValueAsString(eventPayload));
            com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.complete(LOGGER,
                    "exploration_movement_spend", "adventureId=" + adventure.id().value() + " cycleId=" + state.cycle().id()
                            + " cycleNumber=" + state.cycle().cycleNumber() + " groupId=" + group.id()
                            + " operationId=" + result.operationId() + " distance=" + distance + " result=recorded", started);
        } catch (RuntimeException | java.io.IOException failure) {
            com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.fail(LOGGER,
                    "exploration_movement_spend", "adventureId=" + adventure.id().value() + " cycleId=" + state.cycle().id()
                            + " cycleNumber=" + state.cycle().cycleNumber() + " groupId=" + group.id()
                            + " operationId=" + result.operationId() + " distance=" + distance
                            + " result=failed recoveryAction=retry_same_operation_id", started, failure);
            throw new IllegalStateException("could not persist exploration movement spend", failure);
        }
    }

    private com.dndmaster.adventure.domain.adventure.AdventurePartyMember characterForMovementToken(
            Adventure adventure, UUID owner, UUID tokenId) {
        if (adventure.gameState() == null) return characterSheetForToken(adventure, owner, tokenId);
        var state = storedExplorationState(adventure);
        if (state.cycle() == null) return characterSheetForToken(adventure, owner, tokenId);
        var group = state.groups().stream().filter(candidate -> candidate.mapTokenId().equals(tokenId)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("map token does not belong to an exploration group"));
        UUID actingSheetId = group.members().stream()
                .filter(member -> member.formation() == com.dndmaster.adventure.domain.runtime.ExplorationGroup.Formation.FRONT)
                .map(com.dndmaster.adventure.domain.runtime.ExplorationGroup.Member::characterSheetId)
                .sorted().findFirst().orElseThrow(() -> new ApiRequestGuard.ApiContractException(409, "EXPLORATION_GROUP_FRONT_MEMBER_REQUIRED"));
        var member = adventure.party().stream().filter(candidate -> candidate.characterSheetId().value().equals(actingSheetId))
                .findFirst().orElseThrow(() -> new ApiRequestGuard.ApiContractException(409, "EXPLORATION_GROUP_MEMBER_NOT_IN_PARTY"));
        if (com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.enabled())
            LOGGER.info("dev_exploration_movement_actor outcome=resolved adventureId={} groupId={} tokenId={} characterSheetId={} memberCount={}",
                    adventure.id().value(), group.id(), tokenId, actingSheetId, group.members().size());
        return member;
    }

    private void persistMovementStopEvent(Adventure adventure, UUID mapId, UUID tokenId, CombatMapMoveResult result) {
        if (result.status() != com.dndmaster.adventure.application.combat.CombatMapMovementStatus.INTERRUPTED) return;
        if (adventure.gameState() == null) return;
        var state = storedExplorationState(adventure);
        if (state.cycle() == null) return;
        var group = state.groups().stream().filter(candidate -> candidate.mapTokenId().equals(tokenId)).findFirst()
                .orElseThrow(() -> new ApiRequestGuard.ApiContractException(409, "MOVEMENT_TOKEN_NOT_IN_EXPLORATION_GROUP"));
        persistMovementStopEvent(adventure, mapId, state, group, result);
    }

    private void persistMovementStopEvent(Adventure adventure, UUID mapId, CombatMapMoveResult result, boolean resolvePending) {
        if (!resolvePending || result.status() != com.dndmaster.adventure.application.combat.CombatMapMovementStatus.INTERRUPTED) return;
        if (adventure.gameState() == null) return;
        UUID owner = adventure.ownerPlayerId().value();
        var pending = pendingMapMovementConfirmationRepository.findByAdventureId(adventure.id().value(), owner)
                .filter(item -> item.mapId().equals(mapId) && item.confirmationCommandId() != null
                        && item.confirmationCommandId().equals(result.operationId()));
        pending.ifPresent(item -> persistMovementStopEvent(adventure, item.mapId(), item.tokenId(), result));
    }

    private void persistMovementStopEvent(Adventure adventure, UUID mapId,
            com.dndmaster.adventure.domain.runtime.ExplorationState state,
            com.dndmaster.adventure.domain.runtime.ExplorationGroup group, CombatMapMoveResult result) {
        if (result.operationId() == null || result.finalPosition() == null)
            throw new ApiRequestGuard.ApiContractException(502, "MAP_INTERRUPTION_RESULT_INCOMPLETE");
        UUID eventId = UUID.nameUUIDFromBytes((state.cycle().id() + ":movement:" + result.operationId())
                .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        long started = com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.begin(LOGGER,
                "exploration_movement_interruption", "adventureId=" + adventure.id().value() + " cycleId="
                        + state.cycle().id() + " eventId=" + eventId + " operationId=" + result.operationId()
                        + " groupId=" + group.id() + " resultStatus=" + result.status()
                        + " publicEventCount=" + result.publicEvents().size());
        try {
        var event = adjudicateMovementThreatDiscovery(adventure, mapId, state, group, result, eventId);
        var cycle = state.cycle().interruptGroupTurn(group.id(), event);
        if (cycle == state.cycle()) {
            com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.complete(LOGGER,
                    "exploration_movement_interruption", "adventureId=" + adventure.id().value() + " cycleId="
                            + cycle.id() + " eventId=" + eventId + " operationId=" + result.operationId()
                            + " groupId=" + group.id() + " result=already_recorded nextGroupId=" + cycle.currentGroupId(), started);
            return;
        }
        adventure.updateExplorationState(adventure.ownerPlayerId(), adventure.version(), state.withCycle(cycle));
        adventureRepository.save(adventure);
        com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.complete(LOGGER,
                "exploration_movement_interruption", "adventureId=" + adventure.id().value() + " cycleId="
                        + cycle.id() + " eventId=" + eventId + " operationId=" + result.operationId()
                        + " groupId=" + group.id() + " cause=" + (result.interruptionReason() == null ? "unspecified" : result.interruptionReason())
                        + " eventCount=" + result.publicEvents().size() + " finalX=" + result.finalPosition().x()
                        + " finalY=" + result.finalPosition().y() + " result=stored nextGroupId=" + cycle.currentGroupId(), started);
        } catch (RuntimeException | Error failure) {
            com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.fail(LOGGER,
                    "exploration_movement_interruption", "adventureId=" + adventure.id().value() + " cycleId="
                            + state.cycle().id() + " eventId=" + eventId + " operationId=" + result.operationId()
                            + " groupId=" + group.id() + " resultStatus=" + result.status()
                            + " publicEventCount=" + result.publicEvents().size() + " result=failed recoveryAction=retry_same_operation", started, failure);
            throw failure;
        }
    }

    com.dndmaster.adventure.domain.runtime.ExplorationCycle.MovementStopEvent adjudicateMovementThreatDiscovery(
            Adventure adventure, UUID mapId, com.dndmaster.adventure.domain.runtime.ExplorationState state,
            com.dndmaster.adventure.domain.runtime.ExplorationGroup group, CombatMapMoveResult result, UUID eventId) {
        var position = new com.dndmaster.adventure.domain.runtime.ExplorationCycle.Position(
                result.finalPosition().x(), result.finalPosition().y());
        if (!result.publicEvents().contains("HOSTILE_OBSERVED"))
            return new com.dndmaster.adventure.domain.runtime.ExplorationCycle.MovementStopEvent(eventId, group.id(),
                    result.operationId(), result.interruptionReason(), result.publicEvents(), position, result.hostileTokenId());
        long started = com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.begin(LOGGER,
                "exploration_movement_threat_discovery", "adventureId=" + adventure.id().value() + " eventId=" + eventId
                        + " operationId=" + result.operationId() + " groupId=" + group.id()
                        + " memberCount=" + group.members().size() + " publicEventCount=" + result.publicEvents().size());
        try {
            if (result.hostileTokenId() == null)
                throw new ApiRequestGuard.ApiContractException(502, "MOVEMENT_HOSTILE_TOKEN_ID_MISSING");
            UUID owner = adventure.ownerPlayerId().value();
            var map = combatMapViewPort.playerView(adventure.id().value(), owner)
                    .filter(view -> view.mapId().equals(mapId))
                    .orElseThrow(() -> new ApiRequestGuard.ApiContractException(409, "EXPLORATION_MAP_UNAVAILABLE"));
            String gmMap = combatMapViewPort.gmEnvironmentView(mapId, owner);
            var eligibleHostileIds = hostileTokenIds(gmMap);
            if (!eligibleHostileIds.contains(result.hostileTokenId()))
                throw new ApiRequestGuard.ApiContractException(502, "MOVEMENT_HOSTILE_NOT_IN_CURRENT_MAP");
            var groupFacts = state.groups().stream().map(item -> Map.of("groupId", item.id(), "members", item.members().stream()
                    .map(member -> Map.of("characterSheetId", member.characterSheetId(), "formation", member.formation(), "speed", member.speed())).toList())).toList();
            int mapStateOffset = gmMap.indexOf(" GM_MAP_STATE=");
            String movementContext = "MOVEMENT_THREAT_DISCOVERY=true\nMOVEMENT_OPERATION_ID=" + result.operationId()
                    + "\nMOVEMENT_INTERRUPTION_CAUSE=" + (result.interruptionReason() == null ? "unspecified" : result.interruptionReason())
                    + "\nMOVEMENT_FINAL_POSITION=" + objectMapper.valueToTree(position)
                    + "\nHOSTILE_TOKEN_ID=" + result.hostileTokenId() + "\nEXPLORATION_GROUPS=" + objectMapper.valueToTree(groupFacts)
                    + "\nELIGIBLE_HOSTILE_TOKEN_IDS=" + objectMapper.valueToTree(eligibleHostileIds)
                    + (mapStateOffset < 0 ? "\n" + gmMap : "\n" + gmMap.substring(0, mapStateOffset) + gmMap.substring(mapStateOffset));
            UUID discoveryCommandId = UUID.nameUUIDFromBytes((eventId + ":typed-threat-discovery")
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8));
            var generated = runtimeTurnService.planExplorationEnvironmentPhase(new AdventureId(adventure.id().value()),
                    new OwnerPlayerId(owner), discoveryCommandId,
                    com.dndmaster.adventure.domain.runtime.ExplorationCycle.EnvironmentPhase.ENEMIES_PATROLS_AND_DETECTION,
                    movementContext);
            if (generated.encounterRequested() || generated.hostileActionStarted()
                    || !generated.enemyMoves().isEmpty() || !generated.featureInteractions().isEmpty())
                throw new ApiRequestGuard.ApiContractException(502, "MOVEMENT_DISCOVERY_MUST_NOT_START_COMBAT_OR_APPLY_ENVIRONMENT");
            var discovery = generated.threatDiscovery();
            if (discovery == null || discovery.discovererCharacterSheetIds().isEmpty() && !discovery.enemyDiscoveredFirst())
                throw new ApiRequestGuard.ApiContractException(502, "MOVEMENT_THREAT_DISCOVERY_RESULT_MISSING");
            var groupMemberIds = group.members().stream().map(member -> member.characterSheetId()).collect(java.util.stream.Collectors.toSet());
            if (!groupMemberIds.containsAll(discovery.discovererCharacterSheetIds())
                    || !eligibleHostileIds.containsAll(discovery.awareEnemyTokenIds())
                    || discovery.enemyHeardNotification()
                    || (discovery.bothSidesNotHidden() && (discovery.enemyDiscoveredFirst()
                            || !new java.util.HashSet<>(discovery.awareEnemyTokenIds()).equals(eligibleHostileIds)))
                    || (discovery.enemyDiscoveredFirst() && !discovery.awareEnemyTokenIds().contains(result.hostileTokenId())))
                throw new ApiRequestGuard.ApiContractException(502, "MOVEMENT_THREAT_DISCOVERY_IDS_INVALID");
            var discovered = new java.util.HashSet<>(discovery.discovererCharacterSheetIds());
            var awareCharacters = discovery.bothSidesNotHidden() ? groupMemberIds : discovered;
            var event = new com.dndmaster.adventure.domain.runtime.ExplorationCycle.MovementStopEvent(eventId, group.id(),
                    result.operationId(), result.interruptionReason(), result.publicEvents(), position, result.hostileTokenId(),
                    discovered, awareCharacters, false, discovery.awareEnemyTokenIds(), eligibleHostileIds);
            com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.complete(LOGGER,
                    "exploration_movement_threat_discovery", "adventureId=" + adventure.id().value() + " eventId=" + eventId
                            + " operationId=" + result.operationId() + " groupId=" + group.id()
                            + " eligibleHostileCount=" + eligibleHostileIds.size()
                            + " discovererCount=" + discovered.size() + " awareHostileCount=" + discovery.awareEnemyTokenIds().size()
                            + " result=private_discovery_saved", started);
            return event;
        } catch (RuntimeException | Error failure) {
            com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.fail(LOGGER,
                    "exploration_movement_threat_discovery", "adventureId=" + adventure.id().value() + " eventId=" + eventId
                            + " operationId=" + result.operationId() + " groupId=" + group.id()
                            + " memberCount=" + group.members().size() + " publicEventCount=" + result.publicEvents().size()
                            + " result=failed recoveryAction=retry_same_movement_operation", started, failure);
            throw failure;
        }
    }

    @PostMapping("/api/v1/adventures/{adventureId}/combat-map/spatial/observe")
    CombatMapSpatialResponse observeSpatial(@PathVariable UUID adventureId,
            @RequestHeader("Idempotency-Key") UUID idempotencyKey, @RequestBody SpatialActionRequest request) {
        requireSpatialIdempotencyKey(idempotencyKey, request);
        return CombatMapSpatialResponse.from(mapMovementCoordinator.observe(spatialAction(adventureId, request)));
    }

    @PostMapping("/api/v1/adventures/{adventureId}/combat-map/spatial/interact")
    CombatMapSpatialResponse interactSpatial(@PathVariable UUID adventureId,
            @RequestHeader("Idempotency-Key") UUID idempotencyKey, @RequestBody SpatialActionRequest request) {
        requireSpatialIdempotencyKey(idempotencyKey, request);
        return CombatMapSpatialResponse.from(mapMovementCoordinator.interact(spatialAction(adventureId, request)));
    }

    @PostMapping("/api/v1/adventures/{adventureId}/combat-map/spatial/combat-turn-start")
    CombatMapSpatialResponse combatTurnStartSpatial(@PathVariable UUID adventureId,
            @RequestHeader("Idempotency-Key") UUID idempotencyKey, @RequestBody SpatialTurnRequest request) {
        requireSpatialIdempotencyKey(idempotencyKey, request);
        return CombatMapSpatialResponse.from(mapMovementCoordinator.combatTurnStart(spatialTurn(adventureId, request)));
    }

    @PostMapping("/api/v1/adventures/{adventureId}/combat-map/spatial/advance-durations")
    CombatMapSpatialResponse advanceSpatialDurations(@PathVariable UUID adventureId,
            @RequestHeader("Idempotency-Key") UUID idempotencyKey, @RequestBody SpatialTurnRequest request) {
        requireSpatialIdempotencyKey(idempotencyKey, request);
        return CombatMapSpatialResponse.from(mapMovementCoordinator.advanceDurations(spatialTurn(adventureId, request)));
    }

    static void requireSpatialIdempotencyKey(UUID header, SpatialActionRequest request) {
        if (request == null || header == null || request.commandId() == null || !header.equals(request.commandId())) {
            throw new ApiRequestGuard.ApiContractException(400, "IDEMPOTENCY_KEY_MISMATCH");
        }
    }

    static void requireSpatialIdempotencyKey(UUID header, SpatialTurnRequest request) {
        if (request == null || header == null || request.commandId() == null || !header.equals(request.commandId())) {
            throw new ApiRequestGuard.ApiContractException(400, "IDEMPOTENCY_KEY_MISMATCH");
        }
    }

    static void requireSpatialIdempotencyKey(UUID header, SpatialCheckRollRequest request) {
        if (request == null || header == null || request.commandId() == null || !header.equals(request.commandId())) {
            throw new ApiRequestGuard.ApiContractException(400, "IDEMPOTENCY_KEY_MISMATCH");
        }
    }

    static void requireCancelCommandId(UUID header) {
        if (header == null) {
            throw new ApiRequestGuard.ApiContractException(400, "IDEMPOTENCY_KEY_MISMATCH");
        }
    }

    private com.dndmaster.adventure.application.combat.CombatMapSpatialActionCommand spatialAction(
            UUID adventureId, SpatialActionRequest request) {
        if (request == null || request.mapId() == null || request.tokenId() == null || request.commandId() == null
                || request.x() == null || request.y() == null || request.x() < 0 || request.y() < 0
                || request.expectedVersion() == null || request.expectedVersion() < 0) {
            throw new ApiRequestGuard.ApiContractException(400, "INVALID_SPATIAL_ACTION");
        }
        UUID owner = requireSpatialMapOwner(adventureId, request.mapId());
        return new com.dndmaster.adventure.application.combat.CombatMapSpatialActionCommand(adventureId, request.mapId(), owner,
                request.tokenId(), new CombatMapPreviewPosition(request.x(), request.y()), request.expectedVersion(), request.commandId());
    }

    private com.dndmaster.adventure.application.combat.CombatMapSpatialTurnCommand spatialTurn(
            UUID adventureId, SpatialTurnRequest request) {
        if (request == null || request.mapId() == null || request.commandId() == null
                || request.expectedVersion() == null || request.expectedVersion() < 0) {
            throw new ApiRequestGuard.ApiContractException(400, "INVALID_SPATIAL_ACTION");
        }
        UUID owner = requireSpatialMapOwner(adventureId, request.mapId());
        return new com.dndmaster.adventure.application.combat.CombatMapSpatialTurnCommand(request.mapId(), owner,
                request.expectedVersion(), request.commandId());
    }

    private UUID requireSpatialMapOwner(UUID adventureId, UUID mapId) {
        Adventure adventure = adventureRepository.findById(new AdventureId(adventureId)).orElseThrow();
        UUID owner = playerResolver.playerId();
        if (!adventure.ownerPlayerId().value().equals(owner)
                || combatMapViewPort.playerView(adventureId, owner).filter(view -> mapId.equals(view.mapId())).isEmpty()) {
            throw new ApiRequestGuard.ApiContractException(403, "OWNERSHIP_DENIED");
        }
        return owner;
    }

    @GetMapping("/api/v1/adventures/{adventureId}/combat-map/preparation")
    CombatMapResponse preparationMap(@PathVariable UUID adventureId) {
        var adventure = adventureRepository.findById(new AdventureId(adventureId)).orElseThrow();
        UUID owner = playerResolver.playerId();
        if (!adventure.ownerPlayerId().value().equals(owner)) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN);
        return com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.measure(
                LOGGER, "combat_map_preparation_view", "adventureId=" + adventureId,
                () -> combatMapViewPort.preparationView(adventureId, owner)
                        .map(view -> CombatMapResponse.from(adventureId, adventure.version(), view))
                        .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                                org.springframework.http.HttpStatus.NOT_FOUND, "COMBAT_MAP_PREPARATION_NOT_READY")));
    }

    @PutMapping("/api/v1/adventures/{adventureId}/combat-map/calibration")
    CombatMapCalibrationResponse calibrateMap(@PathVariable UUID adventureId, @RequestBody CombatMapCalibrationRequest request) {
        Adventure adventure = adventureRepository.findById(new AdventureId(adventureId)).orElseThrow();
        if (!adventure.ownerPlayerId().value().equals(playerResolver.playerId())) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN);
        }
        com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.measure(
                LOGGER, "combat_map_calibration_save", "adventureId=" + adventureId + " mapId=" + request.mapId()
                        + " expectedVersion=" + request.expectedVersion() + " grid=" + request.width() + "x" + request.height()
                        + " cellSize=" + request.cellSize(),
                () -> combatMapViewPort.calibrate(request.mapId(), playerResolver.playerId(), request.expectedVersion(),
                        request.width(), request.height(), request.cellSize(), request.originX(), request.originY(),
                        request.imageWidth(), request.imageHeight(), request.playerX(), request.playerY()));
        return new CombatMapCalibrationResponse(request.mapId(), request.width(), request.height());
    }

    @PutMapping("/api/v1/adventures/{adventureId}/combat-map/layout")
    void updateMapLayout(@PathVariable UUID adventureId, @RequestBody CombatMapLayoutRequest request) {
        Adventure adventure = adventureRepository.findById(new AdventureId(adventureId)).orElseThrow();
        UUID owner = playerResolver.playerId();
        if (!adventure.ownerPlayerId().value().equals(owner)) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN);
        UUID mapId = editingMap(adventureId, owner).mapId();
        List<com.dndmaster.adventure.application.combat.CombatMapViewPort.Position> obstacles = request.obstacles() == null ? List.of() : request.obstacles().stream().map(p -> new com.dndmaster.adventure.application.combat.CombatMapViewPort.Position(p.x(), p.y())).toList();
        List<com.dndmaster.adventure.application.combat.CombatMapViewPort.Door> doors = request.doors() == null ? List.of() : request.doors().stream().map(p -> new com.dndmaster.adventure.application.combat.CombatMapViewPort.Door(p.x(), p.y(), false)).toList();
        List<com.dndmaster.adventure.application.combat.CombatMapViewPort.Boundary> boundaries = request.boundaries() == null ? List.of() : request.boundaries().stream().map(p -> new com.dndmaster.adventure.application.combat.CombatMapViewPort.Boundary(p.x(), p.y(), p.orientation(), p.kind(), p.open())).toList();
        String context = "adventureId=" + adventureId + " mapId=" + mapId + " commandId=" + request.commandId()
                + " expectedVersion=" + request.expectedVersion() + " boundaryCount=" + boundaries.size()
                + " hasCrop=" + (request.crop() != null) + " alignmentVersion=" + request.alignmentVersion();
        com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.measure(
                LOGGER, "combat_map_layout_save", context,
                () -> combatMapViewPort.updateLayout(mapId, owner, request.expectedVersion(), request.commandId(),
                        obstacles, doors, boundaries, request.crop(), request.alignmentVersion(), request.imageRevision(),
                        request.playerStart() == null ? null
                                : new com.dndmaster.adventure.application.combat.CombatMapViewPort.Position(
                                        request.playerStart().x(), request.playerStart().y())));
    }

    @PostMapping("/api/v1/adventures/{adventureId}/combat-map/detect-boundaries")
    com.dndmaster.adventure.application.combat.CombatMapViewPort.BoundaryProposal detectMapBoundaries(@PathVariable UUID adventureId) {
        Adventure adventure = adventureRepository.findById(new AdventureId(adventureId)).orElseThrow();
        UUID owner = playerResolver.playerId();
        if (!adventure.ownerPlayerId().value().equals(owner)) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN);
        String context = "adventureId=" + adventureId;
        return com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.measure(
                LOGGER, "combat_map_boundary_detection", context, () -> {
                    UUID mapId = com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.measure(
                            LOGGER, "combat_map_boundary_detection_map_lookup", context,
                            () -> combatMapViewPort.preparationView(adventureId, owner).orElseThrow().mapId());
                    return com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.measure(
                            LOGGER, "combat_map_boundary_detection_model_call", context + " mapId=" + mapId,
                            () -> combatMapViewPort.detectMapBoundaries(mapId, owner));
                });
    }

    @GetMapping("/api/v1/adventures/{adventureId}/combat-map/alignment")
    CombatMapAlignmentResponse mapAlignment(@PathVariable UUID adventureId) {
        Adventure adventure = adventureRepository.findById(new AdventureId(adventureId)).orElseThrow();
        UUID owner = playerResolver.playerId();
        if (!adventure.ownerPlayerId().value().equals(owner)) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN);
        UUID mapId = editingMap(adventureId, owner).mapId();
        return com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.measure(
                LOGGER, "combat_map_alignment_read", "adventureId=" + adventureId + " mapId=" + mapId,
                () -> CombatMapAlignmentResponse.from(combatMapViewPort.alignment(mapId, owner)));
    }

    @GetMapping(value = "/api/v1/adventures/{adventureId}/combat-map/alignment/image/{imageViewId}", produces = "image/png")
    ResponseEntity<byte[]> mapAlignmentImage(@PathVariable UUID adventureId, @PathVariable String imageViewId) {
        Adventure adventure = adventureRepository.findById(new AdventureId(adventureId)).orElseThrow();
        UUID owner = playerResolver.playerId();
        if (!adventure.ownerPlayerId().value().equals(owner)) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN);
        UUID mapId = editingMap(adventureId, owner).mapId();
        byte[] image = com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.measure(
                LOGGER, "combat_map_alignment_image_read", "adventureId=" + adventureId + " mapId=" + mapId,
                () -> combatMapViewPort.alignmentImage(mapId, owner, imageViewId));
        if (com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.enabled()) {
            LOGGER.info("dev_flow operation=combat_map_alignment_image_size mapId={} bytes={}", mapId, image.length);
        }
        return ResponseEntity.ok().contentType(org.springframework.http.MediaType.IMAGE_PNG)
                .cacheControl(org.springframework.http.CacheControl.noStore()).body(image);
    }

    @GetMapping(value = "/api/v1/adventures/{adventureId}/combat-map/preparation-image", produces = "image/png")
    ResponseEntity<byte[]> preparationMapImage(@PathVariable UUID adventureId) {
        Adventure adventure = adventureRepository.findById(new AdventureId(adventureId)).orElseThrow();
        UUID owner = playerResolver.playerId();
        if (!adventure.ownerPlayerId().value().equals(owner)) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN);
        var editingMap = editingMap(adventureId, owner);
        var source = sourceForPreparationImage(adventure);
        String sourceDetails = source.map(AdventureController::preparationImageSourceDiagnostics)
                .orElse(" sourceDocumentId=none sourceAssetLocatorKind=none sourceAssetLocatorLength=0");
        byte[] image = com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.measure(
                LOGGER, "combat_map_preparation_image_read", "adventureId=" + adventureId + " mapId=" + editingMap.mapId() + sourceDetails,
                () -> source.map(value -> combatMapViewPort.preparationImage(editingMap.mapId(), owner,
                                value.documentId(), value.locator()))
                        .orElseGet(() -> combatMapViewPort.preparationImage(editingMap.mapId(), owner)));
        if (com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.enabled()) {
            LOGGER.info("dev_flow operation=combat_map_preparation_image_size mapId={} bytes={}", editingMap.mapId(), image.length);
        }
        return ResponseEntity.ok().contentType(org.springframework.http.MediaType.IMAGE_PNG)
                .cacheControl(org.springframework.http.CacheControl.noStore()).body(image);
    }

    @PutMapping("/api/v1/adventures/{adventureId}/combat-map/alignment")
    CombatMapAlignmentResponse applyMapAlignment(@PathVariable UUID adventureId, @RequestBody CombatMapAlignmentRequest request) {
        Adventure adventure = adventureRepository.findById(new AdventureId(adventureId)).orElseThrow();
        UUID owner = playerResolver.playerId();
        if (!adventure.ownerPlayerId().value().equals(owner)) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN);
        UUID activeMap = editingMap(adventureId, owner).mapId();
        if (!activeMap.equals(request.mapId())) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND);
        try {
            return com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.measure(
                    LOGGER, "combat_map_alignment_save", "adventureId=" + adventureId + " mapId=" + activeMap
                            + " commandId=" + request.commandId() + " expectedVersion=" + request.expectedVersion(),
                    () -> CombatMapAlignmentResponse.from(combatMapViewPort.applyAlignment(activeMap, owner,
                            new com.dndmaster.adventure.application.combat.CombatMapViewPort.AlignmentRequest(
                                    request.commandId(), request.expectedVersion(), request.imageRevision(),
                                    request.originX(), request.originY(), request.cellSize()))));
        } catch (IllegalStateException exception) {
            if ("map grid alignment conflict".equals(exception.getMessage())) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT, exception.getMessage(), exception);
            throw exception;
        }
    }

    @PostMapping("/api/v1/adventures/{adventureId}/combat-map/analyze-grid")
    com.dndmaster.adventure.application.combat.CombatMapViewPort.GridAnalysis analyzeMapGrid(@PathVariable UUID adventureId, @RequestBody MapGridAnalysisRequest request) {
        Adventure adventure = adventureRepository.findById(new AdventureId(adventureId)).orElseThrow();
        UUID owner = playerResolver.playerId();
        if (!adventure.ownerPlayerId().value().equals(owner)) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN);
        var map = editingMap(adventureId, owner);
        var result = com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.measure(
                LOGGER, "combat_map_grid_analysis", "adventureId=" + adventureId + " mapId=" + map.mapId()
                        + " cropWidth=" + request.width() + " cropHeight=" + request.height(),
                () -> combatMapViewPort.analyzeGrid(map.mapId(), owner, request.x(), request.y(), request.width(), request.height()));
        if (com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.enabled()) {
            LOGGER.info("dev_flow operation=combat_map_grid_analysis_result mapId={} status={} expectedCrop={}x{} resultGrid={}x{} confidence={}",
                    map.mapId(), result.status(), request.width(), request.height(), result.width(), result.height(), result.confidence());
        }
        return result;
    }

    @PostMapping("/api/v1/adventures/{adventureId}/dice-rolls")
    @Deprecated(forRemoval = false)
    @Operation(deprecated = true, summary = "Legacy combat dice path", description = "Use POST combat/actions with Idempotency-Key and If-Match-Version.")
    ResponseEntity<DiceRollResponse> diceRoll(
            @PathVariable UUID adventureId, @RequestBody DiceRollRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) UUID idempotencyKey) {
        if (request.endCombat()) {
            throw new ApiRequestGuard.ApiContractException(410, "LEGACY_COMBAT_END_UNSUPPORTED");
        }
        UUID authenticatedOwner = playerResolver.playerId();
        if (!CombatActorRole.PLAYER.name().equals(request.role())) {
            throw new ApiRequestGuard.ApiContractException(400, "INVALID_COMBAT_ROLE");
        }
        if (request.damageAmount() != null && request.damageAmount() <= 0) {
            throw new ApiRequestGuard.ApiContractException(400, "INVALID_DAMAGE_AMOUNT");
        }
        if (request.damageAmount() != null && request.targetCharacterSheetId() == null) {
            throw new ApiRequestGuard.ApiContractException(400, "INVALID_COMBAT_TARGET");
        }
        var adventure = adventureRepository.findById(new AdventureId(adventureId)).orElseThrow();
        if (!adventure.ownerPlayerId().value().equals(authenticatedOwner)) {
            throw new ApiRequestGuard.ApiContractException(403, "OWNERSHIP_DENIED");
        }
        RuleSetId requestedRuleSet = new RuleSetId(request.ruleSetId());
        if (!adventure.ruleSetId().equals(requestedRuleSet)) {
            throw new ApiRequestGuard.ApiContractException(400, "INVALID_RULE_SET");
        }
        CharacterSheetId requestedSheet = new CharacterSheetId(request.characterSheetId());
        boolean partyMember = adventure.party().stream()
                .anyMatch(member -> member.characterSheetId().equals(requestedSheet));
        if (!partyMember) {
            throw new ApiRequestGuard.ApiContractException(403, "CHARACTER_NOT_IN_ADVENTURE");
        }
        CombatActionCommand command = new CombatActionCommand(
                legacyDiceCommandId(idempotencyKey),
                adventure.id(),
                adventure.sessionId().value(),
                requestedRuleSet,
                requestedSheet,
                request.combatMapId(),
                CombatActorRole.valueOf(request.role()),
                request.action(),
                null,
                authenticatedOwner,
                request.tokenId(),
                request.expectedVersion(), request.targetArmorClass(), request.attackModifier(),
                request.targetCharacterSheetId() == null ? null : new CharacterSheetId(request.targetCharacterSheetId()), request.damageAmount(), request.endCombat());
        var result = combatActionService.submit(command);
        return ResponseEntity.ok().header("Deprecation", "true")
                .header("Warning", "299 dnd-master \"Legacy combat dice path is deprecated; use combat/actions\"")
                .header("Sunset", LEGACY_SCENARIO_UPLOAD_SUNSET)
                .body(new DiceRollResponse(result.operationId(), CombatActorRole.PLAYER.name(),
                        result.diceTotal() == null ? List.of() : List.of(result.diceTotal()),
                        result.diceTotal() == null ? 0 : result.diceTotal(), result.judgment(), result.status(),
                        "COMMITTED".equals(result.status())));
    }

    static UUID legacyDiceCommandId(UUID idempotencyKey) {
        return idempotencyKey == null ? UUID.randomUUID() : idempotencyKey;
    }

    @PutMapping("/api/v1/adventures/{adventureId}/save")
    SaveAdventureResponse saveAdventure(
            @PathVariable UUID adventureId, @RequestBody SaveAdventureRequest request) {
        UUID authenticatedOwner = playerResolver.playerId();
        savedAdventureService.preserveProgress(
                new AdventureId(adventureId),
                new OwnerPlayerId(authenticatedOwner),
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
        UUID authenticatedOwner = playerResolver.playerId();
        savedAdventureService.deleteAdventure(
                new AdventureId(adventureId),
                new OwnerPlayerId(authenticatedOwner),
                request.expectedVersion());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/internal/v1/adventures")
    List<AdventureSummaryResponse> ownedAdventures(@RequestParam UUID ownerId) {
        return savedAdventureService.listSavedAdventures(new OwnerPlayerId(ownerId)).stream()
                .map(a -> new AdventureSummaryResponse(a.id().value(), a.status().name(), a.version(), a.sessionId().value(), scenarioBundleId(a)))
                .toList();
    }

    private UUID scenarioBundleId(Adventure adventure) {
        UUID packageId = adventure.lockedScenarioPackageId();
        if (packageId == null || scenarioPackageRepository == null) return null;
        return scenarioPackageRepository.findById(packageId).map(scenarioPackage -> scenarioPackage.bundleId().value()).orElse(null);
    }

    @GetMapping("/internal/v1/adventures/{adventureId}/edition")
    EditionResponse appliedEdition(@PathVariable UUID adventureId) {
        Adventure adventure = adventureRepository.findById(new AdventureId(adventureId)).orElseThrow();
        return appliedEdition(adventure);
    }

    private EditionResponse appliedEdition(Adventure adventure) {
        var ruleSetId = new com.dndmaster.adventure.domain.ruleset.RuleSetId(adventure.ruleSetId().value());
        var owner = new com.dndmaster.adventure.domain.ruleset.OwnerPlayerId(adventure.ownerPlayerId().value());
        com.dndmaster.adventure.domain.ruleset.AppliedRuleSet applied;
        try {
            applied = appliedRuleSetService.readRuleSet(ruleSetId, owner);
        } catch (com.dndmaster.adventure.application.ruleset.AppliedRuleSetNotFoundException missing) {
            String context = "adventureId=" + adventure.id().value() + " ruleSetId=" + ruleSetId.value()
                    + " ownerPlayerId=" + adventure.ownerPlayerId().value();
            boolean restored = adventureSessionService != null
                    && com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.measure(
                            LOGGER, "applied_rule_set_recovery", context + " expected=missing actual=missing",
                            () -> adventureSessionService.restoreAppliedRuleSet(
                                    new AdventureId(adventure.id().value()),
                                    new com.dndmaster.adventure.domain.adventure.OwnerPlayerId(adventure.ownerPlayerId().value()),
                                    ruleSetId.value()));
            if (com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.enabled()) {
                LOGGER.info("dev_flow operation=applied_rule_set_recovery outcome={} adventureId={} ruleSetId={} expected=missing actual={} nextAction={}",
                        restored ? "recovered" : "unavailable", adventure.id().value(), ruleSetId.value(),
                        restored ? "persisted" : "missing", restored ? "retry_rule_set_read" : "return_original_failure");
            }
            if (!restored) throw missing;
            applied = appliedRuleSetService.readRuleSet(ruleSetId, owner);
        }
        return new EditionResponse(adventure.id().value(), applied.edition().value());
    }

    @GetMapping("/internal/v1/adventures/{adventureId}/roll-conditions")
    RollConditionsResponse rollConditions(@PathVariable UUID adventureId) {
        return new RollConditionsResponse(adventureId, "standard");
    }

    @PostMapping("/internal/v1/adventures/{adventureId}/movement-validations")
    MovementValidationResponse validateMovement(
            @PathVariable UUID adventureId, @RequestBody MovementValidationRequest request) {
        Adventure adventure = adventureRepository.findById(new AdventureId(adventureId)).orElseThrow();
        if (request == null || request.tokenId() == null) return new MovementValidationResponse(adventureId, false, "invalid");
        try {
            var view = combatMapViewPort.playerView(adventureId, adventure.ownerPlayerId().value()).orElseThrow();
            UUID mapId = request.mapId() == null ? view.mapId() : request.mapId();
            if (!mapId.equals(view.mapId()) || request.x() < 0 || request.y() < 0
                    || request.mapVersion() != null && request.mapVersion() < 0
                    || request.waypoints() != null && (request.waypoints().size() > CombatMapPreviewCommand.MAX_WAYPOINTS
                            || request.waypoints().stream().anyMatch(AdventureController::invalidPreviewPosition))) {
                return new MovementValidationResponse(adventureId, false, "invalid");
            }
            long mapVersion = request.mapVersion() == null ? view.version() : request.mapVersion();
            combatMapPort.preview(new CombatMapPreviewCommand(mapId, adventure.ownerPlayerId().value(), request.tokenId(),
                    new CombatMapPreviewPosition(request.x(), request.y()),
                    request.waypoints() == null ? List.of() : request.waypoints().stream().map(AdventureController::toPreviewPosition).toList(),
                    appliedEdition(adventure).edition(), mapVersion));
            return new MovementValidationResponse(adventureId, true, "valid");
        } catch (RuntimeException invalid) {
            return new MovementValidationResponse(adventureId, false, "invalid");
        }
    }

    private static CombatMapPreviewPosition toPreviewPosition(PositionPayload position) {
        return new CombatMapPreviewPosition(position.x(), position.y());
    }

    private static List<CombatMapPreviewPosition> previewWaypoints(List<PositionPayload> waypoints) {
        return waypoints == null ? List.of() : waypoints.stream().map(AdventureController::toPreviewPosition).toList();
    }

    private static boolean invalidPreviewPosition(PositionPayload position) {
        return position == null || position.x() == null || position.y() == null || position.x() < 0 || position.y() < 0;
    }

    private String publicMovementMap(com.dndmaster.adventure.application.combat.CombatMapViewPort.View view) {
        var root = objectMapper.createObjectNode();
        root.put("mapId", view.mapId().toString()).put("version", view.version());
        var grid = root.putObject("grid");
        if (view.grid() != null) grid.put("width", view.grid().width()).put("height", view.grid().height())
                .put("cellSize", view.grid().cellSize()).put("distanceUnit", view.grid().distanceUnit());
        var tokens = root.putArray("tokens");
        view.tokens().stream().filter(token -> "PLAYER".equals(token.type()) || view.current().stream()
                .anyMatch(cell -> cell.x() == token.x() && cell.y() == token.y())).forEach(token ->
                tokens.addObject().put("id", token.id().toString()).put("type", token.type()).put("x", token.x()).put("y", token.y()));
        var obstacles = root.putArray("obstacles");
        view.obstacles().forEach(position -> obstacles.addObject().put("x", position.x()).put("y", position.y()));
        var doors = root.putArray("doors");
        view.doors().forEach(door -> doors.addObject().put("x", door.x()).put("y", door.y()).put("open", door.open()));
        var current = root.putArray("visibleCells");
        view.current().forEach(position -> current.addObject().put("x", position.x()).put("y", position.y()));
        var explored = root.putArray("exploredCells");
        view.explored().forEach(position -> explored.addObject().put("x", position.x()).put("y", position.y()));
        return root.toString();
    }

    private String currentPosition(com.dndmaster.adventure.application.combat.CombatMapViewPort.View view, UUID tokenId) {
        return view.tokens().stream().filter(token -> token.id().equals(tokenId)).findFirst()
                .map(token -> token.x() + "," + token.y()).orElse("");
    }

    public record GmTurnRequest(UUID turnId, GmInputRequest input) {}

    public record PlayerRollRequest(int result, long expectedVersion) {}

    public record GmInputRequest(String type, String text, UUID mapId, Long mapVersion, String action, String question,
            String previewFingerprint) {
        public GmInputRequest(String type, String text, UUID mapId, Long mapVersion, String action, String question) {
            this(type, text, mapId, mapVersion, action, question, null);
        }
        com.dndmaster.adventure.domain.runtime.GmInput toDomain() {
            if (type == null) throw new IllegalArgumentException("input type is required");
            return switch (type) {
                case "TEXT" -> new com.dndmaster.adventure.domain.runtime.GmInput.TextInput(text);
                case "MAP_ACTION" -> {
                    if (mapId == null || mapVersion == null || mapVersion < 0 || action == null || action.isBlank()) {
                        throw new ApiRequestGuard.ApiContractException(400, "INVALID_MAP_MOVE_PREVIEW");
                    }
                    yield new com.dndmaster.adventure.domain.runtime.GmInput.MapActionInput(mapId, mapVersion, action);
                }
                case "META_QUESTION" -> new com.dndmaster.adventure.domain.runtime.GmInput.MetaQuestionInput(question);
                default -> throw new IllegalArgumentException("unsupported input type: " + type);
            };
        }
        String actionText() { return toDomain().actionText(); }
    }

    private GmInputRequest normalizeMapMovementInput(GmInputRequest input) {
        if (input == null || !"MAP_ACTION".equals(input.type())
                || input.previewFingerprint() == null || input.previewFingerprint().isBlank()
                || input.action() == null || input.action().isBlank()) return input;
        try {
            JsonNode parsed = objectMapper.readTree(input.action());
            if (parsed == null || !parsed.isObject()) return input;
            com.fasterxml.jackson.databind.node.ObjectNode payload = (com.fasterxml.jackson.databind.node.ObjectNode) parsed;
            payload.put("fingerprint", input.previewFingerprint());
            return new GmInputRequest(input.type(), input.text(), input.mapId(), input.mapVersion(),
                    objectMapper.writeValueAsString(payload), input.question(), input.previewFingerprint());
        } catch (java.io.IOException ignored) {
            return input;
        }
    }
    // 프런트가 바로 보여줄 수 있게 턴 결과를 압축한 응답이다.
    public record RuntimeTurnResponse(
            UUID turnId,
            UUID adventureId,
            UUID scenarioPackageId,
            long bindingVersion,
            String narration,
            String currentScene,
            List<String> visibleFacts,
            long version,
            com.dndmaster.adventure.application.runtime.PlayerRollRequest rollRequest,
            com.dndmaster.adventure.application.combat.CombatMapMoveResult movementResult) {
        static RuntimeTurnResponse from(RuntimeTurnResult result) {
            return new RuntimeTurnResponse(
                    result.turn().turnId(),
                    result.turn().adventureId().value(),
                    result.turn().scenarioPackageId(),
                    result.turn().bindingVersion(),
                    result.turn().plan().narration(),
                    result.context().currentScene(),
                    result.visibleTurn() == null ? List.of() : result.visibleTurn().visibleFacts(),
                    result.version(), result.visibleTurn() == null ? null : result.visibleTurn().rollRequest(),
                    result.movementResult());
        }
    }
    public record RuleInquiryRequest(UUID inquiryId, UUID ruleSetId, UUID playerId, String situation) {}
    public record RuleInquiryResponse(UUID inquiryId, String status) {}

    /** Validates and translates the typed map action before the Scenario Runtime command. */
    private void applyMapAction(Adventure adventure, UUID owner, UUID commandId,
            com.dndmaster.adventure.domain.runtime.GmInput.MapActionInput input) {
        try {
            MapActionPayload payload = objectMapper.readValue(input.action(), MapActionPayload.class);
            if (payload == null) {
                throw new ApiRequestGuard.ApiContractException(400, "INVALID_MAP_MOVE_PREVIEW");
            }
            if (!input.mapId().equals(payload.mapId()) || payload.mapVersion() == null || input.mapVersion() != payload.mapVersion()) {
                throw new ApiRequestGuard.ApiContractException(400, "INVALID_MAP_MOVE_PREVIEW");
            }
            if (payload.action() == null || payload.action().isBlank()) {
                throw new ApiRequestGuard.ApiContractException(400, "INVALID_MAP_MOVE_PREVIEW");
            }
            if (!"MOVE".equals(payload.action())) {
                throw new ApiRequestGuard.ApiContractException(400, "UNSUPPORTED_MAP_ACTION");
            }
            if (payload.tokenId() == null) {
                throw new ApiRequestGuard.ApiContractException(400, "INVALID_MAP_MOVE_PREVIEW");
            }
            requireActiveExplorationGroup(adventure, payload.mapId(), payload.tokenId());
            var member = characterForMovementToken(adventure, owner, payload.tokenId());
            if (payload.path() == null || payload.path().size() < 2) {
                throw new ApiRequestGuard.ApiContractException(400, "INVALID_MAP_MOVE_PATH");
            }
            CombatMapPreviewResult confirmedPreview = validateConfirmedMapPreview(adventure, owner, payload);
            requireExplorationMovementBudget(adventure, payload.tokenId(), confirmedPreview.distance());
            String path = payload.path().stream()
                    .map(position -> position.x() + "," + position.y()).reduce((left, right) -> left + ";" + right).orElse(null);
            CombatActionCommand command = new CombatActionCommand(commandId, adventure.id(), adventure.sessionId().value(),
                    adventure.ruleSetId(), member.characterSheetId(), payload.mapId(), CombatActorRole.PLAYER,
                    payload.action(), path, owner, payload.tokenId(), payload.mapVersion());
            characterCombatPort.requireUsableCharacter(command);
            CombatMapMoveResult result = mapMovementCoordinator.resolve(new CombatMapMoveCommand(command, confirmedPreview.distance(), payload.mapVersion(),
                    appliedEdition(adventure).edition(), payload.fingerprint(), previewWaypoints(payload.waypoints())));
            recordExplorationMovementSpend(adventure, payload.tokenId(), result);
            pendingMapMovementConfirmationRepository.deleteByAdventureId(adventure.id().value(), owner);
        } catch (java.io.IOException exception) {
            throw new ApiRequestGuard.ApiContractException(400, "INVALID_MAP_MOVE_PREVIEW");
        }
    }

    private com.dndmaster.adventure.application.runtime.RuntimeTurnCommand prepareMapCommand(Adventure adventure, UUID owner,
            UUID turnId, UUID commandId,
            com.dndmaster.adventure.domain.runtime.GmInput.MapActionInput input) {
        try {
            MapActionPayload payload = objectMapper.readValue(input.action(), MapActionPayload.class);
            if (payload == null) {
                throw new ApiRequestGuard.ApiContractException(400, "INVALID_MAP_MOVE_PREVIEW");
            }
            if (!input.mapId().equals(payload.mapId()) || payload.mapVersion() == null || input.mapVersion() != payload.mapVersion()) {
                throw new ApiRequestGuard.ApiContractException(400, "INVALID_MAP_MOVE_PREVIEW");
            }
            if (payload.action() == null || payload.action().isBlank()) {
                throw new ApiRequestGuard.ApiContractException(400, "INVALID_MAP_MOVE_PREVIEW");
            }
            if (!"MOVE".equals(payload.action())) {
                throw new ApiRequestGuard.ApiContractException(400, "UNSUPPORTED_MAP_ACTION");
            }
            if (payload.tokenId() == null) {
                throw new ApiRequestGuard.ApiContractException(400, "INVALID_MAP_MOVE_PREVIEW");
            }
            requireActiveExplorationGroup(adventure, payload.mapId(), payload.tokenId());
            var member = characterForMovementToken(adventure, owner, payload.tokenId());
            if (payload.path() == null || payload.path().size() < 2) {
                throw new ApiRequestGuard.ApiContractException(400, "INVALID_MAP_MOVE_PATH");
            }
            CombatMapPreviewResult confirmedPreview = validateConfirmedMapPreview(adventure, owner, payload);
            requireExplorationMovementBudget(adventure, payload.tokenId(), confirmedPreview.distance());
            String path = payload.path() == null ? null : payload.path().stream()
                    .map(position -> position.x() + "," + position.y()).reduce((left, right) -> left + ";" + right).orElse(null);
            CombatActionCommand command = new CombatActionCommand(
                    commandId, adventure.id(), adventure.sessionId().value(), adventure.ruleSetId(), member.characterSheetId(), payload.mapId(),
                    CombatActorRole.PLAYER, payload.action(), path, owner, payload.tokenId(), payload.mapVersion());
            characterCombatPort.requireUsableCharacter(command);
            UUID runtimeCommandId = UUID.nameUUIDFromBytes((commandId + ":combat-map").getBytes(java.nio.charset.StandardCharsets.UTF_8));
            var targetContext = objectMapper.createObjectNode()
                    .put("ruleSetId", adventure.ruleSetId().value().toString())
                    .put("characterSheetId", member.characterSheetId().value().toString())
                    .put("combatMapId", payload.mapId().toString())
                    .put("tokenId", payload.tokenId().toString())
                    .put("expectedVersion", payload.mapVersion())
                    .put("distance", confirmedPreview.distance())
                    .put("appliedEdition", appliedEdition(adventure).edition())
                    .put("fingerprint", payload.fingerprint());
            var waypoints = targetContext.putArray("waypoints");
            if (payload.waypoints() != null) {
                payload.waypoints().forEach(position -> waypoints.addObject().put("x", position.x()).put("y", position.y()));
            }
            return com.dndmaster.adventure.application.runtime.RuntimeTurnCommand.create(turnId, runtimeCommandId,
                    adventure.id().value(), adventure.sessionId().value(), owner, targetContext.toString(),
                    "combat-map.move", input.action(), 0);
        } catch (java.io.IOException exception) {
            throw new ApiRequestGuard.ApiContractException(400, "INVALID_MAP_MOVE_PREVIEW");
        }
    }

    private static UUID canonicalPlayerTokenId(UUID characterSheetId) {
        return UUID.nameUUIDFromBytes(("player-" + Objects.requireNonNull(characterSheetId))
                .getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    /** 준비 단계와 플레이 단계 모두에서 같은 맵을 편집 대상으로 선택한다. */
    private com.dndmaster.adventure.application.combat.CombatMapViewPort.View editingMap(UUID adventureId, UUID owner) {
        return combatMapViewPort.preparationView(adventureId, owner)
                .or(() -> combatMapViewPort.playerView(adventureId, owner))
                .orElseThrow();
    }

    private java.util.Optional<MapImageSource> sourceForPreparationImage(Adventure adventure) {
        UUID packageId = adventure.lockedScenarioPackageId();
        if (packageId == null || scenarioPackageRepository == null) return java.util.Optional.empty();
        return scenarioPackageRepository.findById(packageId)
                .flatMap(packageVersion -> packageVersion.initialMapDefinition(adventure.currentContext().currentScene()))
                .map(AdventureController::sourceForPreparationImage);
    }

    static MapImageSource sourceForPreparationImage(com.dndmaster.adventure.domain.scenario.MapDefinition map) {
        return new MapImageSource(map.source().knowledgeDocumentId().value(), map.assetLocator());
    }

    static String preparationImageSourceDiagnostics(MapImageSource source) {
        String locator = source.locator();
        String locatorKind = java.util.regex.Pattern.compile("(?i)\\bpage\\s+\\d+\\b").matcher(locator).find()
                ? "page" : "other";
        return " sourceDocumentId=" + source.documentId()
                + " sourceAssetLocatorKind=" + locatorKind
                + " sourceAssetLocatorLength=" + locator.length()
                + " sourceAssetLocatorFingerprint="
                + com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.fingerprint(locator);
    }

    record MapImageSource(UUID documentId, String locator) {}

    private com.dndmaster.adventure.domain.adventure.AdventurePartyMember characterSheetForToken(
            Adventure adventure, UUID owner, UUID tokenId) {
        return CombatMapPlayerTokenResolver.resolve(adventure, owner, tokenId, combatMapViewPort);
    }

    private UUID currentRuntimeTurnId(Adventure adventure) {
        return runtimeTurnRepository.findAllByAdventureId(adventure.id()).stream()
                .filter(turn -> turn.adventureId().equals(adventure.id())
                        && turn.sessionId().equals(adventure.sessionId().value())
                        && isActivePlayerRuntimeTurn(adventure, turn))
                .reduce((first, second) -> second)
                .map(com.dndmaster.adventure.application.runtime.RuntimeTurn::turnId)
                .orElseThrow(() -> new ApiRequestGuard.ApiContractException(409, "MOVEMENT_CONFIRMATION_TURN_NOT_FOUND"));
    }

    private void validatePendingRuntimeTurn(Adventure adventure, UUID pendingTurnId) {
        if (pendingTurnId == null) throw new ApiRequestGuard.ApiContractException(409, "MOVEMENT_CONFIRMATION_TURN_NOT_FOUND");
        var turn = runtimeTurnRepository.findByTurnId(pendingTurnId)
                .orElseThrow(() -> new ApiRequestGuard.ApiContractException(409, "MOVEMENT_CONFIRMATION_TURN_NOT_FOUND"));
        if (!turn.adventureId().equals(adventure.id()) || !turn.sessionId().equals(adventure.sessionId().value())) {
            throw new ApiRequestGuard.ApiContractException(409, "STALE_MOVEMENT_PROPOSAL");
        }
        if (turn.lifecycle() == com.dndmaster.adventure.application.runtime.RuntimeTurnLifecycle.DISCARDED
                || turn.lifecycle() == com.dndmaster.adventure.application.runtime.RuntimeTurnLifecycle.COMMIT_REPAIR_REQUIRED) {
            throw new ApiRequestGuard.ApiContractException(409, "MOVEMENT_CONFIRMATION_TURN_NOT_FOUND");
        }
        if (!isActivePlayerRuntimeTurn(adventure, turn)) {
            throw new ApiRequestGuard.ApiContractException(409, "STALE_MOVEMENT_PROPOSAL");
        }
    }

    private boolean isActivePlayerRuntimeTurn(Adventure adventure,
            com.dndmaster.adventure.application.runtime.RuntimeTurn turn) {
        var lifecycle = turn.lifecycle();
        return turn.origin() == com.dndmaster.adventure.application.runtime.RuntimeTurnOrigin.PLAYER
                && turn.playerOrigin() && !turn.gmOnly() && !turn.agentOrigin()
                && lifecycle != null && !lifecycle.isCommitted()
                && lifecycle != com.dndmaster.adventure.application.runtime.RuntimeTurnLifecycle.DISCARDED
                && lifecycle != com.dndmaster.adventure.application.runtime.RuntimeTurnLifecycle.COMMIT_REPAIR_REQUIRED
                && (turn.turnCharacterSheetId() == null || adventure.party().stream()
                        .anyMatch(member -> member.controlMode() == com.dndmaster.adventure.domain.adventure.ControlMode.DIRECT
                                && member.characterSheetId().equals(turn.turnCharacterSheetId())));
    }

    private CombatMapMoveResult readTerminalMovementResult(PendingMapMovementConfirmation pending) {
        if (pending.movementResultJson() == null || pending.movementResultJson().isBlank()) {
            throw new ApiRequestGuard.ApiContractException(409, "MOVEMENT_CONFIRMATION_NOT_FOUND");
        }
        try {
            return objectMapper.readValue(pending.movementResultJson(), CombatMapMoveResult.class);
        } catch (java.io.IOException exception) {
            throw new ApiRequestGuard.ApiContractException(409, "MOVEMENT_CONFIRMATION_NOT_FOUND");
        }
    }

    private String writeTerminalMovementResult(CombatMapMoveResult result) {
        try {
            return objectMapper.writeValueAsString(result);
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("could not persist terminal movement result", exception);
        }
    }

    private CombatMapPreviewResult validateConfirmedMapPreview(Adventure adventure, UUID owner, MapActionPayload payload) {
        if (payload.mapVersion() == null || payload.mapVersion() < 0) {
            throw new ApiRequestGuard.ApiContractException(400, "INVALID_MAP_MOVE_PREVIEW");
        }
        if (payload.waypoints() == null || payload.waypoints().size() > CombatMapPreviewCommand.MAX_WAYPOINTS
                || payload.waypoints().stream().anyMatch(AdventureController::invalidPreviewPosition)) {
            throw new ApiRequestGuard.ApiContractException(400, "INVALID_MAP_MOVE_PREVIEW");
        }
        if (payload.fingerprint() == null || payload.fingerprint().isBlank()) {
            throw new ApiRequestGuard.ApiContractException(400, "INVALID_MAP_MOVE_PREVIEW");
        }
        if (payload.path() == null || payload.path().size() < 2 || payload.tokenId() == null
                || payload.path().stream().anyMatch(AdventureController::invalidPreviewPosition)
                || payload.location() != null && invalidPreviewPosition(payload.location())) {
            throw new ApiRequestGuard.ApiContractException(400, "INVALID_MAP_MOVE_PREVIEW");
        }
        PositionPayload destination = payload.location() == null ? payload.path().getLast() : payload.location();
        var preview = combatMapPort.preview(new CombatMapPreviewCommand(
                payload.mapId(), owner, payload.tokenId(), toPreviewPosition(destination),
                payload.waypoints() == null ? List.of() : payload.waypoints().stream().map(AdventureController::toPreviewPosition).toList(),
                appliedEdition(adventure).edition(), payload.mapVersion()));
        List<PositionPayload> previewPath = preview.orderedPositions().stream()
                .map(position -> new PositionPayload(position.x(), position.y())).toList();
        if (!payload.fingerprint().equals(preview.fingerprint()) || !payload.path().equals(previewPath)) {
            throw new ApiRequestGuard.ApiContractException(409, "MOVEMENT_PREVIEW_MISMATCH");
        }
        return preview;
    }

    public record MapActionPayload(UUID mapId, Long mapVersion, UUID tokenId, String action,
            List<PositionPayload> path, UUID targetId, PositionPayload location,
            List<PositionPayload> waypoints, String fingerprint) {}
    public record PositionPayload(Integer x, Integer y) {}
    public record CombatMapResponse(UUID adventureId, String status, long sessionVersion, UUID mapId,
            com.dndmaster.adventure.application.combat.CombatMapViewPort.Grid grid,
            List<com.dndmaster.adventure.application.combat.CombatMapViewPort.Token> tokens,
            List<com.dndmaster.adventure.application.combat.CombatMapViewPort.Obstacle> obstacles,
            List<com.dndmaster.adventure.application.combat.CombatMapViewPort.Door> doors,
            List<com.dndmaster.adventure.application.combat.CombatMapViewPort.Layer> layers,
            List<com.dndmaster.adventure.application.combat.CombatMapViewPort.Position> current,
            List<com.dndmaster.adventure.application.combat.CombatMapViewPort.Position> explored, Long version,
            List<com.dndmaster.adventure.application.combat.CombatMapViewPort.StartCandidate> playerStartCandidates,
            List<com.dndmaster.adventure.application.combat.CombatMapViewPort.SpatialFeature> spatialFeatures) {
        public CombatMapResponse(UUID adventureId, String status, long sessionVersion, UUID mapId,
                com.dndmaster.adventure.application.combat.CombatMapViewPort.Grid grid,
                List<com.dndmaster.adventure.application.combat.CombatMapViewPort.Token> tokens,
                List<com.dndmaster.adventure.application.combat.CombatMapViewPort.Obstacle> obstacles,
                List<com.dndmaster.adventure.application.combat.CombatMapViewPort.Door> doors,
                List<com.dndmaster.adventure.application.combat.CombatMapViewPort.Layer> layers,
                List<com.dndmaster.adventure.application.combat.CombatMapViewPort.Position> current,
                List<com.dndmaster.adventure.application.combat.CombatMapViewPort.Position> explored, Long version) {
            this(adventureId, status, sessionVersion, mapId, grid, tokens, obstacles, doors, layers, current, explored, version, List.of(), List.of());
        }
        static CombatMapResponse from(UUID adventureId, long sessionVersion, com.dndmaster.adventure.application.combat.CombatMapViewPort.View view) {
            return new CombatMapResponse(adventureId, "authoritative-map", sessionVersion, view.mapId(), view.grid(), view.tokens(), view.obstacles(), view.doors(), view.layers(), view.current(), view.explored(), view.version(), view.playerStartCandidates(), view.spatialFeatures());
        }
    }
    public record CombatMapCalibrationRequest(UUID mapId, long expectedVersion, int width, int height, int cellSize,
            int originX, int originY, int imageWidth, int imageHeight, Integer playerX, Integer playerY) {}
    public record CombatMapCalibrationResponse(UUID mapId, int width, int height) {}
    public record MapGridAnalysisRequest(int x, int y, int width, int height) {}
    public record CombatMapLayoutRequest(UUID commandId, long expectedVersion, List<PositionPayload> obstacles,
                                         List<PositionPayload> doors, List<BoundaryPayload> boundaries, String crop,
                                         Long alignmentVersion, String imageRevision, PositionPayload playerStart) {
        public CombatMapLayoutRequest(UUID commandId, long expectedVersion, List<PositionPayload> obstacles,
                List<PositionPayload> doors, List<BoundaryPayload> boundaries, String crop) {
            this(commandId, expectedVersion, obstacles, doors, boundaries, crop, null, "", null);
        }
    }
    public record BoundaryPayload(int x, int y, String orientation, String kind, boolean open) {}
    public record CombatMapAlignmentRequest(UUID mapId, UUID commandId, long expectedVersion, String imageRevision,
                                            double originX, double originY, double cellSize) {}
    public record CombatMapAlignmentResponse(UUID mapId, long version, String imageRevision, String imageViewId, double originX, double originY, double cellSize) {
        static CombatMapAlignmentResponse from(com.dndmaster.adventure.application.combat.CombatMapViewPort.Alignment alignment) {
            return new CombatMapAlignmentResponse(alignment.mapId(), alignment.version(), alignment.imageRevision(), alignment.imageViewId(), alignment.originX(), alignment.originY(), alignment.cellSize());
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
            String action, Integer targetArmorClass, Integer attackModifier, UUID targetCharacterSheetId, Integer damageAmount, boolean endCombat) {
        public DiceRollRequest(UUID ruleSetId, UUID characterSheetId, UUID combatMapId, UUID ownerPlayerId, UUID tokenId,
                long expectedVersion, String role, String action) {
            this(ruleSetId, characterSheetId, combatMapId, ownerPlayerId, tokenId, expectedVersion, role, action, null, null, null, null, false);
        }
    }
    public record DiceRollResponse(UUID rollId, String scope, List<Integer> faces, int total,
            String judgment, String resolutionStatus, boolean outcomeApplied) {
        public DiceRollResponse(UUID rollId, String scope, List<Integer> faces, int total) {
            this(rollId, scope, faces, total, "판정 결과를 사용할 수 없습니다.", "PENDING_RULE_INPUT", false);
        }
    }
    public record SaveAdventureRequest(UUID playerId, long expectedVersion, String currentScene) {}
    public record SaveAdventureResponse(UUID adventureId, long newVersion) {}
    public record DeleteAdventureRequest(UUID playerId, long expectedVersion) {}
    public record AdventureSummaryResponse(UUID adventureId, String status, long version, UUID sessionId, UUID scenarioBundleId) {}
    public record EditionResponse(UUID adventureId, String edition) {}
    public record RollConditionsResponse(UUID adventureId, String conditions) {}
    public record CombatMapMovementPreviewRequest(UUID mapId, Long mapVersion, UUID tokenId,
            PositionPayload destination, List<PositionPayload> waypoints, UUID pendingTurnId, String sourceText) {
        public CombatMapMovementPreviewRequest(UUID mapId, Long mapVersion, UUID tokenId,
                PositionPayload destination, List<PositionPayload> waypoints) {
            this(mapId, mapVersion, tokenId, destination, waypoints, null, null);
        }
    }
    public record NaturalLanguageMovementPreviewRequest(UUID mapId, Long mapVersion, UUID tokenId, String sourceText, String tacticalContext) {}
    public record NaturalLanguageMovementConfirmationRequest(UUID pendingTurnId, UUID commandId, UUID tokenId, Long mapVersion) {}
    public record NaturalLanguageMovementPreviewResponse(String status, PositionPayload destination,
            List<MovementPlacementModelPort.Candidate> candidates, String playerMessage, UUID pendingTurnId,
            UUID confirmationCommandId, List<PositionPayload> path, Integer distance, Long baseMapVersion, String fingerprint) {
        static NaturalLanguageMovementPreviewResponse from(MovementPlacementModelPort.MovementPlacementProposal proposal,
                CombatMapPreviewResult preview, UUID pendingTurnId, UUID confirmationCommandId) {
            return new NaturalLanguageMovementPreviewResponse(proposal.status(), proposal.destination() == null ? null
                    : new PositionPayload(proposal.destination().x(), proposal.destination().y()), proposal.candidates(), proposal.playerMessage(), pendingTurnId,
                    confirmationCommandId,
                    preview == null ? List.of() : preview.orderedPositions().stream().map(position -> new PositionPayload(position.x(), position.y())).toList(),
                    preview == null ? null : preview.distance(), preview == null ? null : preview.baseMapVersion(), preview == null ? null : preview.fingerprint());
        }
    }
    public record NaturalLanguageMovementConfirmationResponse(UUID operationId, String status, long version, long resultingVersion,
            List<PositionPayload> requestedPath, List<PositionPayload> traversedPath, PositionPayload finalPosition,
            List<String> publicEvents, String interruptionReason,
            com.dndmaster.adventure.application.combat.CombatMapPendingCheck pendingCheck,
            com.dndmaster.adventure.application.combat.MovementFollowUpCommand followUp) {
        static NaturalLanguageMovementConfirmationResponse from(CombatMapMoveResult result) {
            return new NaturalLanguageMovementConfirmationResponse(result.operationId(), result.status().name(), result.version(), result.version(),
                    result.requestedPath().stream().map(position -> new PositionPayload(position.x(), position.y())).toList(),
                    result.traversedPath().stream().map(position -> new PositionPayload(position.x(), position.y())).toList(),
                    result.finalPosition() == null ? null : new PositionPayload(result.finalPosition().x(), result.finalPosition().y()),
                    result.publicEvents(), result.interruptionReason(), result.pendingCheck(), result.followUp());
        }
    }
    public record CombatMapMovementPreviewResponse(UUID mapId, List<PositionPayload> orderedPositions,
            int distance, long baseMapVersion, String fingerprint) {
        static CombatMapMovementPreviewResponse from(CombatMapPreviewResult preview) {
            return new CombatMapMovementPreviewResponse(preview.mapId(), preview.orderedPositions().stream()
                    .map(position -> new PositionPayload(position.x(), position.y())).toList(),
                    preview.distance(), preview.baseMapVersion(), preview.fingerprint());
        }
    }
    public record SpatialActionRequest(UUID mapId, UUID tokenId, Integer x, Integer y, Long expectedVersion, UUID commandId) {}
    public record SpatialTurnRequest(UUID mapId, Long expectedVersion, UUID commandId) {}
    public record SpatialCheckRollRequest(UUID mapId, UUID operationId, UUID checkId, UUID ownerPlayerId,
            UUID commandId, long expectedVersion) {}
    public record CombatMapSpatialResponse(UUID mapId, long mapVersion, List<String> publicEvents, UUID operationId,
            String status, com.dndmaster.adventure.application.combat.CombatMapPendingCheck pendingCheck) {
        public CombatMapSpatialResponse(UUID mapId, long mapVersion, List<String> publicEvents) {
            this(mapId, mapVersion, publicEvents, null, null, null);
        }
        static CombatMapSpatialResponse from(com.dndmaster.adventure.application.combat.CombatMapSpatialResult result) {
            return new CombatMapSpatialResponse(result.mapId(), result.version(), result.publicEvents(), result.operationId(),
                    result.status(), result.pendingCheck());
        }
    }
    public record MovementValidationRequest(UUID tokenId, int x, int y, UUID mapId, Long mapVersion,
            List<PositionPayload> waypoints) {
        public MovementValidationRequest(UUID tokenId, int x, int y) {
            this(tokenId, x, y, null, null, List.of());
        }
    }
    public record MovementValidationResponse(UUID adventureId, boolean valid, String reason) {}
}
