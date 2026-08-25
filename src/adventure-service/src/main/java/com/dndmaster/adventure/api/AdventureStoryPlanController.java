package com.dndmaster.adventure.api;

import com.dndmaster.adventure.application.storyplan.AdventureStoryPlanApplicationService;
import com.dndmaster.adventure.domain.adventure.AdventureStoryPlan;
import com.dndmaster.adventure.domain.adventure.SessionId;
import com.dndmaster.adventure.domain.adventure.OwnerPlayerId;
import com.dndmaster.adventure.domain.adventure.AdventurePlanConfiguration;
import com.dndmaster.adventure.domain.adventure.AdventureLength;
import java.util.UUID;
import java.util.List;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.beans.factory.annotation.Autowired;
import com.dndmaster.adventure.application.session.AdventureSessionApplicationService;
import com.dndmaster.adventure.application.runtime.TacticalMapActivationApplicationService;
import com.dndmaster.adventure.application.runtime.TacticalTriggerRuntimeApplicationService;
import com.dndmaster.adventure.application.storyplan.FutureTacticalSceneRevisionService;
import com.dndmaster.adventure.application.storyplan.AdventureStoryPlanGenerationJobService;
import com.dndmaster.adventure.application.runtime.TacticalScenePreparationApplicationService;
import com.dndmaster.adventure.domain.adventure.TacticalScenePlan;

@RestController
@RequestMapping("/api/v1/adventure-sessions/{sessionId}/story-plan")
public final class AdventureStoryPlanController {
    private final AdventureStoryPlanApplicationService service;
    private final AdventureSessionApplicationService sessions;
    private final TacticalMapActivationApplicationService mapActivation;
    private final AuthenticatedPlayerResolver playerResolver;
    private final TacticalTriggerRuntimeApplicationService triggerRuntime;
    private final FutureTacticalSceneRevisionService futureRevision;
    private final ApiRequestGuard requestGuard;
    private final AdventureStoryPlanGenerationJobService generationJobs;
    private final TacticalScenePreparationApplicationService tacticalPreparation;

    @Autowired
    public AdventureStoryPlanController(AdventureStoryPlanApplicationService service, AdventureSessionApplicationService sessions,
            TacticalMapActivationApplicationService mapActivation, AuthenticatedPlayerResolver playerResolver,
            TacticalTriggerRuntimeApplicationService triggerRuntime, FutureTacticalSceneRevisionService futureRevision,
            @org.springframework.beans.factory.annotation.Value("${adventure.integration.internal-token:${INTERNAL_SERVICE_TOKEN:}}") String internalToken,
            AdventureStoryPlanGenerationJobService generationJobs,
            TacticalScenePreparationApplicationService tacticalPreparation) {
        this(service, sessions, mapActivation, playerResolver, triggerRuntime, futureRevision,
                new ApiRequestGuard(internalToken), generationJobs, tacticalPreparation);
    }

    public AdventureStoryPlanController(AdventureStoryPlanApplicationService service, AdventureSessionApplicationService sessions,
            TacticalMapActivationApplicationService mapActivation, AuthenticatedPlayerResolver playerResolver,
            TacticalTriggerRuntimeApplicationService triggerRuntime, FutureTacticalSceneRevisionService futureRevision,
            ApiRequestGuard requestGuard) {
        this(service, sessions, mapActivation, playerResolver, triggerRuntime, futureRevision, requestGuard, null, null);
    }

    public AdventureStoryPlanController(AdventureStoryPlanApplicationService service, AdventureSessionApplicationService sessions,
            TacticalMapActivationApplicationService mapActivation, AuthenticatedPlayerResolver playerResolver,
            TacticalTriggerRuntimeApplicationService triggerRuntime, FutureTacticalSceneRevisionService futureRevision,
            ApiRequestGuard requestGuard, AdventureStoryPlanGenerationJobService generationJobs) {
        this(service, sessions, mapActivation, playerResolver, triggerRuntime, futureRevision, requestGuard, generationJobs, null);
    }

    public AdventureStoryPlanController(AdventureStoryPlanApplicationService service, AdventureSessionApplicationService sessions,
            TacticalMapActivationApplicationService mapActivation, AuthenticatedPlayerResolver playerResolver,
            TacticalTriggerRuntimeApplicationService triggerRuntime, FutureTacticalSceneRevisionService futureRevision,
            ApiRequestGuard requestGuard, AdventureStoryPlanGenerationJobService generationJobs,
            TacticalScenePreparationApplicationService tacticalPreparation) {
        this.service = service; this.sessions = sessions; this.mapActivation = mapActivation; this.playerResolver = playerResolver; this.triggerRuntime = triggerRuntime; this.futureRevision = futureRevision;
        this.requestGuard = requestGuard; this.generationJobs = generationJobs; this.tacticalPreparation = tacticalPreparation;
    }

    @GetMapping
    PlayerPlanView read(@PathVariable UUID sessionId) { return PlayerPlanView.from(service.read(new SessionId(sessionId), owner())); }

    List<PlayerPlanView> history(@PathVariable UUID sessionId) {
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "story plan history is GM-only");
    }

    @GetMapping("/history")
    List<GmPlanView> history(@PathVariable UUID sessionId,
            @RequestHeader(value = "X-Internal-Token", required = false) String internalToken) {
        requestGuard.internal(internalToken);
        try {
            return service.readHistoryEntries(new SessionId(sessionId), owner()).stream().map(GmPlanView::from).toList();
        } catch (SecurityException denied) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "adventure session access denied", denied);
        }
    }

    @GetMapping("/gm")
    GmPlanView gm(@PathVariable UUID sessionId,
            @RequestHeader(value = "X-Internal-Token", required = false) String internalToken) {
        requestGuard.internal(internalToken);
        return GmPlanView.from(service.read(new SessionId(sessionId), owner()));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    GenerationJobView generate(@PathVariable UUID sessionId, @RequestBody(required = false) ConfigurationRequest request) {
        if (generationJobs == null) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "story plan generation jobs are unavailable");
        return GenerationJobView.from(generationJobs.start(new SessionId(sessionId), owner(), request == null ? AdventurePlanConfiguration.defaults() : request.toDomain()));
    }

    @PostMapping("/retry")
    @ResponseStatus(HttpStatus.ACCEPTED)
    GenerationJobView retry(@PathVariable UUID sessionId, @RequestBody(required = false) ConfigurationRequest request) {
        if (generationJobs == null) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "story plan generation jobs are unavailable");
        return GenerationJobView.from(generationJobs.start(new SessionId(sessionId), owner(), request == null ? AdventurePlanConfiguration.defaults() : request.toDomain()));
    }

    @GetMapping("/generation/{jobId}")
    GenerationJobView generation(@PathVariable UUID sessionId, @PathVariable UUID jobId) {
        if (generationJobs == null) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "story plan generation jobs are unavailable");
        return GenerationJobView.from(generationJobs.read(jobId, new SessionId(sessionId), owner()));
    }

    @PostMapping("/stages/{position}/activate-map")
    MapActivationView activateMap(@PathVariable UUID sessionId, @PathVariable int position) {
        var session = sessions.read(new SessionId(sessionId), owner());
        if (session.startedAdventureId() == null) throw new ResponseStatusException(HttpStatus.CONFLICT, "adventure must be started before map activation");
        var plan = service.read(new SessionId(sessionId), owner());
        if (plan.currentStage() + 1 != position) throw new ResponseStatusException(HttpStatus.CONFLICT, "only the current stage may be activated");
        var stage = plan.stages().stream().filter(item -> item.position() == position).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "story plan stage not found"));
        if (stage.mapDefinitionId() == null) throw new ResponseStatusException(HttpStatus.CONFLICT, "stage has no tactical map");
        if (tacticalPreparation != null) {
            if (plan.currentStage() + 1 != position) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "only the current stage may be prepared");
            }
            var preparation = tacticalPreparation.prepare(new SessionId(sessionId), owner());
            if (preparation.status() != TacticalScenePreparationApplicationService.Status.COMPLETE) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        preparation.message() + " 사유: " + preparation.failureReason());
            }
            plan = service.read(new SessionId(sessionId), owner());
            stage = plan.stages().stream().filter(item -> item.position() == position).findFirst().orElseThrow();
        }
        var activation = mapActivation.activateDefinition(session.scenarioPackageId(), session.startedAdventureId().value(), owner().value(),
                session.runtimeConfiguration().ruleSetId().value(), stage.mapDefinitionId(), stage.tacticalScenePlan(), stage.playerSpawnX(), stage.playerSpawnY());
        activation.combatMapId().ifPresent(id -> triggerRuntime.bindActiveMap(session.startedAdventureId().value(), position, owner().value(), id));
        return new MapActivationView(position, stage.mapDefinitionId(), stage.mapAssetId(), stage.mapAssetLocator(), activation.combatMapId().orElse(null));
    }

    @PostMapping("/stages/{position}/tactical-scene/prepare")
    TacticalPreparationView prepareTacticalScene(@PathVariable UUID sessionId, @PathVariable int position) {
        if (tacticalPreparation == null) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "tactical preparation is unavailable");
        var plan = service.read(new SessionId(sessionId), owner());
        if (plan.currentStage() + 1 != position) throw new ResponseStatusException(HttpStatus.CONFLICT, "only the current stage may be prepared");
        var view = tacticalPreparation.prepare(new SessionId(sessionId), owner());
        if (view.stagePosition() != position) throw new ResponseStatusException(HttpStatus.CONFLICT, "only the current stage may be prepared");
        return TacticalPreparationView.from(view);
    }

    @GetMapping("/stages/{position}/tactical-scene/prepare")
    TacticalPreparationView readTacticalScenePreparation(@PathVariable UUID sessionId, @PathVariable int position) {
        if (tacticalPreparation == null) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "tactical preparation is unavailable");
        return TacticalPreparationView.from(tacticalPreparation.read(new SessionId(sessionId), owner(), position));
    }

    @PostMapping("/stages/{position}/tactical-scene/retry")
    TacticalPreparationView retryTacticalScene(@PathVariable UUID sessionId, @PathVariable int position) {
        if (tacticalPreparation == null) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "tactical preparation is unavailable");
        var plan = service.read(new SessionId(sessionId), owner());
        if (plan.currentStage() + 1 != position) throw new ResponseStatusException(HttpStatus.CONFLICT, "only the current stage may be retried");
        var view = tacticalPreparation.retry(new SessionId(sessionId), owner());
        if (view.stagePosition() != position) throw new ResponseStatusException(HttpStatus.CONFLICT, "only the current stage may be retried");
        return TacticalPreparationView.from(view);
    }

    @PostMapping("/stages/{position}/tactical-scene/revise")
    PlayerPlanView reviseFutureTacticalScene(@PathVariable UUID sessionId, @PathVariable int position,
        @RequestHeader(value = "X-Internal-Token", required = false) String internalToken,
            @RequestBody RevisionRequest request) {
        requestGuard.internal(internalToken);
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "revision request is required");
        }
        return PlayerPlanView.from(futureRevision.revise(new SessionId(sessionId), owner(), position, request.causingGmTurnId(), request.causingGmCommandId()));
    }

    @PostMapping("/stages/{position}/triggers/{triggerId}/apply")
    TriggerApplicationView applyTrigger(@PathVariable UUID sessionId, @PathVariable int position,
            @PathVariable String triggerId, @RequestBody(required = false) TriggerApplicationRequest request) {
        if (request == null || request.qualifyingAction() == null || request.qualifyingAction().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "qualifyingAction is required");
        }
        var session = sessions.read(new SessionId(sessionId), owner());
        var plan = service.read(new SessionId(sessionId), owner());
        if (session.status() != com.dndmaster.adventure.domain.adventure.AdventureSession.Status.STARTED
                || session.startedAdventureId() == null) throw new ResponseStatusException(HttpStatus.CONFLICT, "adventure is not active");
        if (plan.currentStage() + 1 != position) throw new ResponseStatusException(HttpStatus.CONFLICT, "trigger stage is not the active stage");
        var stage = plan.stages().stream().filter(item -> item.position() == position).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "story plan stage not found"));
        if (!stage.tacticalScenePlan().readyForActivation()) throw new ResponseStatusException(HttpStatus.CONFLICT, "tactical scene is not ready");
        if (session.startedAdventureId() == null) throw new ResponseStatusException(HttpStatus.CONFLICT, "adventure must be started");
        final var evaluation = applyTriggerChecked(session, position, stage, triggerId, request);
        return new TriggerApplicationView(evaluation.triggerId(), evaluation.type(), evaluation.targetIds(), evaluation.transitionId());
    }

    private com.dndmaster.adventure.application.runtime.TacticalTriggerEvaluator.Evaluation applyTriggerChecked(com.dndmaster.adventure.domain.adventure.AdventureSession session,
            int position, com.dndmaster.adventure.domain.adventure.AdventureStoryPlanStage stage, String triggerId, TriggerApplicationRequest request) {
        try {
            return triggerRuntime.apply(session.startedAdventureId().value(), position, stage.tacticalScenePlan(), triggerId,
                    request.qualifyingAction(), request.combatMapId(), owner().value(), request.expectedVersion(), request.commandId());
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        }
    }

    public record MapActivationView(int stagePosition, UUID mapDefinitionId, String assetId, String assetLocator, UUID combatMapId) {}
    public record TacticalPreparationView(UUID jobId, UUID sessionId, int stagePosition, String stageName, String status,
            int progress, int attempts, boolean mapRequired, String message, String failureReason, java.time.Instant updatedAt) {
        static TacticalPreparationView from(TacticalScenePreparationApplicationService.PreparationView view) {
            return new TacticalPreparationView(view.jobId(), view.sessionId(), view.stagePosition(), view.stageName(),
                    view.status().name(), view.progress(), view.attempts(), view.mapRequired(), view.message(), view.failureReason(), view.updatedAt());
        }
    }
    public record TriggerApplicationRequest(UUID combatMapId, UUID commandId, long expectedVersion, String qualifyingAction) {
        public TriggerApplicationRequest(UUID combatMapId, UUID commandId, long expectedVersion) {
            this(combatMapId, commandId, expectedVersion, null);
        }
    }
    public record TriggerApplicationView(String triggerId, String type, List<String> targetIds, String transitionId) {}
    public record RevisionRequest(UUID causingGmTurnId, UUID causingGmCommandId) {}

    private OwnerPlayerId owner() { return new OwnerPlayerId(playerResolver.playerId()); }

    public record PlanView(UUID planId, long packageRevision, long partyRevision, long version, String status, int currentStage, int stageCount,
            int endingCount, String adventureLength, List<StageView> stages, String failureReason) {
        static PlanView from(AdventureStoryPlan plan) { return new PlanView(plan.planId(), plan.packageRevision(), plan.partyRevision(), plan.version(), plan.status().name(), plan.currentStage(), plan.stageCount(), plan.configuration().endingCount(), plan.configuration().adventureLength().name(), plan.stages().stream().map(StageView::from).toList(), plan.failureReason()); }
    }

    public record PlayerPlanView(String status, int currentStage, List<PlayerStageView> stages, int planRevision,
            int endingCount, String adventureLength,
            @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
            String failureReason) {
        static PlayerPlanView from(AdventureStoryPlan plan) {
            List<StageView> visible = plan.stages().stream()
                    .filter(stage -> stage.position() == plan.currentStage() + 1)
                    .map(StageView::from)
                    .toList();
            return new PlayerPlanView(plan.status().name(), plan.currentStage(),
                    visible.stream().map(PlayerStageView::from).toList(), 0,
                    plan.configuration().endingCount(), plan.configuration().adventureLength().name(),
                    plan.failureReason());
        }
    }

    public record PlayerStageView(int position, String title, String stageType, String location, String goal) {
        static PlayerStageView from(StageView stage) {
            return new PlayerStageView(stage.position(), stage.title(), stage.stageType(), stage.location(), stage.goal());
        }
    }

    public record GenerationJobView(UUID jobId, UUID sessionId, String status, int progress, String stage, String message, java.time.Instant updatedAt) {
        static GenerationJobView from(AdventureStoryPlanGenerationJobService.JobView job) {
            return new GenerationJobView(job.jobId(), job.sessionId(), job.status().name(), job.progress(), job.stage(), job.message(), job.updatedAt());
        }
    }

    public record StageView(int position, String title, String stageType, String location, String goal,
            List<String> rewards, UUID mapDefinitionId, String mapAssetId, String mapAssetLocator,
            String groundingStatus, List<String> aiSuggestions, String mapSafetyStatus, Double mapConfidence, int evidenceCount,
            int playerSpawnX, int playerSpawnY, String playerSpawnConfidence, String playerSpawnRationale) {
        static StageView from(com.dndmaster.adventure.domain.adventure.AdventureStoryPlanStage stage) {
            return new StageView(stage.position(), stage.title(), stage.stageType().name(), stage.location(), stage.goal(), stage.rewards(), stage.mapDefinitionId(), stage.mapAssetId(), stage.mapAssetLocator(),
                    stage.groundingStatus().name(), stage.aiSuggestions(), stage.mapSafetyStatus(), stage.mapConfidence(), stage.evidence().size(),
                    stage.playerSpawnX(), stage.playerSpawnY(), stage.playerSpawnConfidence(), stage.playerSpawnRationale());
        }
    }
    public record EvidenceView(String documentType, UUID documentId, long extractionVersion, String locator, String quote, double confidence) {}

    public record GmPlanView(UUID planId, long packageRevision, long partyRevision, long version, String status, int currentStage,
            int endingCount, String adventureLength, List<GmStageView> stages, String failureReason, String auditId,
            java.time.Instant recordedAt, String cause, String predecessorHistoryId) {
        static GmPlanView from(com.dndmaster.adventure.application.storyplan.AdventureStoryPlanHistoryEntry entry) {
            AdventureStoryPlan plan = entry.plan();
            return new GmPlanView(plan.planId(), plan.packageRevision(), plan.partyRevision(), plan.version(), plan.status().name(), plan.currentStage(),
                    plan.configuration().endingCount(), plan.configuration().adventureLength().name(), plan.stages().stream().map(GmStageView::from).toList(), plan.failureReason(),
                    entry.historyId().toString(), entry.recordedAt(), entry.cause(), entry.predecessorHistoryId() == null ? null : entry.predecessorHistoryId().toString());
        }
        static GmPlanView from(AdventureStoryPlan plan) {
            return new GmPlanView(plan.planId(), plan.packageRevision(), plan.partyRevision(), plan.version(), plan.status().name(), plan.currentStage(),
                    plan.configuration().endingCount(), plan.configuration().adventureLength().name(), plan.stages().stream().map(GmStageView::from).toList(), plan.failureReason(),
                    plan.planId().toString(), plan.updatedAt(), "CURRENT", null);
        }
    }
    public record GmStageView(int position, String title, String stageType, String location, String goal, String conflict,
            String clearCondition, String failureCondition, List<String> enemies, String boss, String transitionCondition,
            List<String> rewards, List<String> branchIds, List<String> endingIds,
            UUID mapDefinitionId, String mapAssetId, String mapAssetLocator, String mapSafetyStatus, Double mapConfidence,
            java.util.Map<String, String> branchTargets,
            String groundingStatus, List<String> aiSuggestions, List<String> executionNotes, List<EvidenceView> evidence,
            GmTacticalSceneView tacticalScene) {
            static GmStageView from(com.dndmaster.adventure.domain.adventure.AdventureStoryPlanStage stage) {
            return new GmStageView(stage.position(), stage.title(), stage.stageType().name(), stage.location(), stage.goal(), stage.conflict(), stage.clearCondition(), stage.failureCondition(),
                    stage.enemies(), stage.boss(), stage.transitionCondition(), stage.rewards(), stage.branchIds(), stage.endingIds(), stage.mapDefinitionId(), stage.mapAssetId(), stage.mapAssetLocator(), stage.mapSafetyStatus(), stage.mapConfidence(), stage.branchTargets(),
                    stage.groundingStatus().name(), stage.aiSuggestions(), stage.npcOrClues(), stage.evidence().stream().map(item -> new EvidenceView(item.documentType(), item.documentId(), item.extractionVersion(), item.locator(), item.quote(), item.confidence())).toList(),
                    GmTacticalSceneView.from(stage.tacticalScenePlan()));
        }
    }

    public record GmTacticalSceneView(String status, List<TacticalPlacementView> placements,
            List<TacticalEnvironmentView> environments, List<TacticalTriggerView> triggers, List<TacticalOutcomeView> outcomes,
            List<CoordinateView> hiddenRegions, List<String> transitionIds,
            com.dndmaster.adventure.domain.adventure.TacticalScenePlan plan) {
        static GmTacticalSceneView from(com.dndmaster.adventure.domain.adventure.TacticalScenePlan scene) {
            java.util.List<com.dndmaster.adventure.domain.adventure.TacticalPlacement> all = new java.util.ArrayList<>();
            all.addAll(scene.players()); all.addAll(scene.allies()); all.addAll(scene.npcs()); all.addAll(scene.enemies());
            all.addAll(scene.bosses()); all.addAll(scene.interactiveObjects());
            return new GmTacticalSceneView(scene.status().name(), all.stream().map(TacticalPlacementView::from).toList(),
                    scene.environments().stream().map(TacticalEnvironmentView::from).toList(),
                    scene.triggers().stream().map(TacticalTriggerView::from).toList(),
                    scene.outcomes().stream().map(TacticalOutcomeView::from).toList(),
                    scene.initialFog() == null ? List.of() : scene.initialFog().hiddenRegions().stream().map(CoordinateView::from).toList(), scene.transitionIds(), scene);
        }
    }
    public record TacticalPlacementView(String id, String kind, CoordinateView coordinate, String groundingType, String citation, String rationale) {
        static TacticalPlacementView from(com.dndmaster.adventure.domain.adventure.TacticalPlacement value) {
            return new TacticalPlacementView(value.id(), value.kind().name(), CoordinateView.from(value.coordinate()), value.grounding().type().name(), value.grounding().citation(), value.grounding().rationale());
        }
    }
    public record TacticalEnvironmentView(String id, String kind, CoordinateView coordinate, String groundingType, String citation, String rationale) {
        static TacticalEnvironmentView from(com.dndmaster.adventure.domain.adventure.TacticalEnvironment value) {
            return new TacticalEnvironmentView(value.id(), value.kind(), CoordinateView.from(value.coordinate()), value.grounding().type().name(), value.grounding().citation(), value.grounding().rationale());
        }
    }
    public record TacticalTriggerView(String id, String type, List<String> targetIds, String transitionId,
            String qualifyingAction, String groundingType, String citation, String rationale) {
        static TacticalTriggerView from(com.dndmaster.adventure.domain.adventure.TacticalTrigger value) {
            return new TacticalTriggerView(value.id(), value.type().name(), value.targetIds(), value.transitionId(), value.qualifyingAction(), value.grounding().type().name(), value.grounding().citation(), value.grounding().rationale());
        }
    }
    public record TacticalOutcomeView(String id, String condition, String groundingType, String citation, String rationale) {
        static TacticalOutcomeView from(com.dndmaster.adventure.domain.adventure.TacticalOutcome value) {
            return new TacticalOutcomeView(value.id(), value.condition(), value.grounding().type().name(), value.grounding().citation(), value.grounding().rationale());
        }
    }
    public record CoordinateView(double x, double y) {
        static CoordinateView from(com.dndmaster.adventure.domain.adventure.NormalizedCoordinate value) { return new CoordinateView(value.x(), value.y()); }
    }

    public record ConfigurationRequest(Integer endingCount, String adventureLength) {
        AdventurePlanConfiguration toDomain() {
            try {
                return new AdventurePlanConfiguration(endingCount == null ? 2 : endingCount,
                        adventureLength == null ? AdventureLength.STANDARD : AdventureLength.valueOf(adventureLength));
            } catch (IllegalArgumentException exception) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid adventure plan configuration", exception);
            }
        }
    }
}
