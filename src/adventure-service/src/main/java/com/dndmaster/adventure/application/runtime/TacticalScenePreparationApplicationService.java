package com.dndmaster.adventure.application.runtime;

import com.dndmaster.adventure.application.session.AdventureSessionRepository;
import com.dndmaster.adventure.application.storyplan.AdventureStoryPlanGenerationPort;
import com.dndmaster.adventure.application.storyplan.AdventureStoryPlanGenerationPort.SourceCitation;
import com.dndmaster.adventure.application.storyplan.AdventureStoryPlanRepository;
import com.dndmaster.adventure.application.storyplan.TacticalScenePlanCandidate;
import com.dndmaster.adventure.application.storyplan.TacticalScenePlanValidator;
import com.dndmaster.adventure.application.storyplan.TacticalSceneRequest;
import com.dndmaster.adventure.domain.adventure.AdventurePlanEvidence;
import com.dndmaster.adventure.domain.adventure.AdventureSession;
import com.dndmaster.adventure.domain.adventure.AdventureStoryPlan;
import com.dndmaster.adventure.domain.adventure.AdventureStoryPlanStage;
import com.dndmaster.adventure.domain.adventure.OwnerPlayerId;
import com.dndmaster.adventure.domain.adventure.SessionId;
import com.dndmaster.adventure.domain.adventure.TacticalPreparationRequirement;
import com.dndmaster.adventure.domain.adventure.TacticalScenePlan;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Composes lazy tactical intent and executes preparation only for the current required stage. */
public final class TacticalScenePreparationApplicationService {
    private static final int MAX_ATTEMPTS = 3;
    private final AdventureStoryPlanRepository plans;
    private final AdventureSessionRepository sessions;
    private final AdventureStoryPlanGenerationPort generator;
    private final TacticalScenePlanValidator validator;
    private final TacticalScenePreparationJobRepository jobs;
    private final TacticalPreparationStatePolicy statePolicy;
    private final boolean inlineLegacyMode;

    public TacticalScenePreparationApplicationService(AdventureStoryPlanRepository plans, AdventureSessionRepository sessions,
            AdventureStoryPlanGenerationPort generator, TacticalScenePlanValidator validator) {
        this(plans, sessions, generator, validator, new InMemoryTacticalScenePreparationJobRepository(), true);
    }

    public TacticalScenePreparationApplicationService(AdventureStoryPlanRepository plans, AdventureSessionRepository sessions,
            AdventureStoryPlanGenerationPort generator, TacticalScenePlanValidator validator,
            TacticalScenePreparationJobRepository jobs) {
        this(plans, sessions, generator, validator, jobs, false);
    }

    private TacticalScenePreparationApplicationService(AdventureStoryPlanRepository plans, AdventureSessionRepository sessions,
            AdventureStoryPlanGenerationPort generator, TacticalScenePlanValidator validator,
            TacticalScenePreparationJobRepository jobs, boolean inlineLegacyMode) {
        this.plans = Objects.requireNonNull(plans);
        this.sessions = Objects.requireNonNull(sessions);
        this.generator = Objects.requireNonNull(generator);
        this.validator = Objects.requireNonNull(validator);
        this.jobs = Objects.requireNonNull(jobs);
        this.inlineLegacyMode = inlineLegacyMode;
        this.statePolicy = new TacticalPreparationStatePolicy();
        for (var job : jobs.findUnfinished()) {
            var recovered = job;
            if (job.status() == TacticalScenePreparationJobRepository.Status.RUNNING) {
                jobs.update(job.jobId(), TacticalScenePreparationJobRepository.Status.QUEUED, job.progress(),
                        job.attempts(), "재접속 후 준비 작업이 복원되었습니다.", job.failureReason());
                recovered = jobs.find(job.sessionId(), job.stagePosition()).orElse(job);
            }
            if (inlineLegacyMode) resume(recovered);
        }
    }

    public PreparationView prepare(SessionId sessionId, OwnerPlayerId owner) {
        return player(ensure(sessionId, owner, false));
    }

    public PreparationView retry(SessionId sessionId, OwnerPlayerId owner) {
        return player(ensure(sessionId, owner, true));
    }

    /** Reads any stage without creating a job; future required stages remain REQUIRED_PENDING. */
    public TacticalPreparationReadModel readComposed(SessionId sessionId, OwnerPlayerId owner, int position) {
        authorize(sessionId, owner);
        AdventureStoryPlan plan = plans.findBySessionId(sessionId)
                .orElseThrow(() -> new IllegalStateException("adventure story plan not found"));
        AdventureStoryPlanStage stage = stage(plan, position);
        return compose(sessionId.value(), plan, stage);
    }

    public PreparationView read(SessionId sessionId, OwnerPlayerId owner, int position) {
        return player(readComposed(sessionId, owner, position));
    }

    public PreparationDiagnostics readDiagnostics(SessionId sessionId, OwnerPlayerId owner, int position) {
        var diagnostics = readComposed(sessionId, owner, position).diagnostics();
        return new PreparationDiagnostics(diagnostics.jobStatus(), diagnostics.failureReason(), diagnostics.sceneStatus(),
                diagnostics.mapActivationAllowed(), diagnostics.updatedAt());
    }

    private TacticalPreparationReadModel ensure(SessionId sessionId, OwnerPlayerId owner, boolean retry) {
        AdventureSession session = authorize(sessionId, owner);
        if (session.status() != AdventureSession.Status.STARTED) {
            throw new IllegalStateException("adventure must be started before tactical preparation");
        }
        AdventureStoryPlan plan = plans.findBySessionId(sessionId)
                .orElseThrow(() -> new IllegalStateException("adventure story plan not found"));
        AdventureStoryPlanStage stage = stage(plan, plan.currentStage() + 1);
        if (stage.tacticalPreparationRequirement() != TacticalPreparationRequirement.REQUIRED) {
            return compose(sessionId.value(), plan, stage);
        }

        TacticalScenePreparationJobRepository.Job job = jobs.createOrGet(sessionId.value(), owner.value(),
                stage.position(), stage.title(), true);
        if (retry && job.status() == TacticalScenePreparationJobRepository.Status.FAILED_RETRYABLE) {
            jobs.resetForRetry(job.jobId());
            job = jobs.find(sessionId.value(), stage.position()).orElseThrow();
        }
        if (stage.tacticalScenePlan().readyForActivation()
                && job.status() == TacticalScenePreparationJobRepository.Status.QUEUED) {
            jobs.update(job.jobId(), TacticalScenePreparationJobRepository.Status.COMPLETE, 100, job.attempts(),
                    "전술 장면 준비가 복원되었습니다.", null);
        } else if (inlineLegacyMode && job.status() == TacticalScenePreparationJobRepository.Status.QUEUED) {
            run(job);
        }
        AdventureStoryPlan refreshed = plans.findBySessionId(sessionId).orElseThrow();
        return compose(sessionId.value(), refreshed, stage(refreshed, stage.position()));
    }

    /** Worker entrypoint. Player-facing prepare only enqueues and reads readiness. */
    public void processQueuedJobs() {
        jobs.recoverExpiredLeases(Instant.now());
        for (var job : jobs.findUnfinished()) {
            if (job.status() == TacticalScenePreparationJobRepository.Status.QUEUED) run(job);
        }
    }

    private void resume(TacticalScenePreparationJobRepository.Job job) {
        if (job.status() != TacticalScenePreparationJobRepository.Status.QUEUED) return;
        AdventureStoryPlan plan = plans.findBySessionId(new SessionId(job.sessionId())).orElse(null);
        boolean currentRequired = plan != null
                && plan.currentStage() + 1 == job.stagePosition()
                && stage(plan, job.stagePosition()).tacticalPreparationRequirement() == TacticalPreparationRequirement.REQUIRED;
        if (!currentRequired) {
            jobs.update(job.jobId(), TacticalScenePreparationJobRepository.Status.FAILED_RETRYABLE, job.progress(),
                    job.attempts(), "현재 단계가 아니어서 준비를 실행하지 않았습니다.", "queued job is not for the current required stage");
            return;
        }
        run(job);
    }

    private void run(TacticalScenePreparationJobRepository.Job job) {
        if (!jobs.claim(job.jobId(), UUID.randomUUID(), java.time.Duration.ofMinutes(5))) return;
        AdventureSession session = sessions.findById(new SessionId(job.sessionId()))
                .orElseThrow(() -> new IllegalStateException("adventure session not found"));
        AdventureStoryPlan plan = plans.findBySessionId(new SessionId(job.sessionId()))
                .orElseThrow(() -> new IllegalStateException("adventure story plan not found"));
        AdventureStoryPlanStage stage = stage(plan, job.stagePosition());
        if (stage.tacticalPreparationRequirement() != TacticalPreparationRequirement.REQUIRED
                || stage.mapDefinitionId() == null) {
            jobs.updateProgress(job.jobId(), TacticalScenePreparationJobRepository.Status.FAILED_RETRYABLE,
                    PreparationProgress.of("FAILED", 0, null),
                    job.attempts(), "전술 장면을 준비할 수 없습니다. 다시 시도해 주세요.",
                    "required tactical stage has no map definition");
            return;
        }

        List<SourceCitation> citations = stage.evidence().stream()
                .map(TacticalScenePreparationApplicationService::citation).toList();
        var map = new AdventureStoryPlanGenerationPort.MapContext(stage.mapDefinitionId(), stage.mapAssetId(),
                stage.mapAssetLocator(), stage.mapAssetLocator(), stage.mapConfidence() == null ? 0 : stage.mapConfidence(),
                stage.mapSafetyStatus(), citations, stage.location());
        List<String> party = session.party().stream()
                .map(member -> member.characterSheetId().value().toString()).toList();
        List<String> violations = List.of();
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            jobs.updateProgress(job.jobId(), TacticalScenePreparationJobRepository.Status.RUNNING,
                    PreparationProgress.of("TACTICAL_SCENE", attempt, null), attempt,
                    "전술 장면을 준비하고 있습니다.", null);
            try {
                TacticalSceneRequest request = new TacticalSceneRequest(stage, map, citations, party, violations);
                TacticalScenePlanCandidate candidate = generator.generateTacticalScene(request);
                violations = validator.validate(request, candidate);
                if (violations.isEmpty() && candidate.scene().readyForActivation()) {
                    plans.save(plan.prepareCurrentStage(stage.withTacticalScenePlan(candidate.scene())));
                    jobs.updateProgress(job.jobId(), TacticalScenePreparationJobRepository.Status.COMPLETE,
                            PreparationProgress.of("TACTICAL_SCENE", 1, 1), attempt,
                            "전술 장면이 준비되었습니다.", null);
                    return;
                }
                if (violations.isEmpty()) violations = List.of("tactical scene is absent");
            } catch (RuntimeException failure) {
                violations = List.of(message(failure));
            }
        }
        jobs.updateProgress(job.jobId(), TacticalScenePreparationJobRepository.Status.FAILED_RETRYABLE,
                PreparationProgress.of("TACTICAL_SCENE", 0, null), MAX_ATTEMPTS,
                "전술 장면 준비에 실패했습니다. 다시 시도해 주세요.", String.join("; ", violations));
    }

    private TacticalPreparationReadModel compose(UUID sessionId, AdventureStoryPlan plan, AdventureStoryPlanStage stage) {
        Optional<TacticalScenePreparationJobRepository.Job> job = jobs.find(sessionId, stage.position());
        boolean current = stage.position() == plan.currentStage() + 1;
        TacticalScenePlan scene = stage.tacticalScenePlan();
        TacticalPreparationState state = statePolicy.compose(stage.tacticalPreparationRequirement(), current, job, scene);
        int progress = job.map(TacticalScenePreparationJobRepository.Job::progress)
                .orElse(state == TacticalPreparationState.READY ? 100 : 0);
        PreparationProgress preparationProgress = job.map(TacticalScenePreparationJobRepository.Job::preparationProgress)
                .orElse(PreparationProgress.legacy(progress));
        int attempts = job.map(TacticalScenePreparationJobRepository.Job::attempts).orElse(0);
        Instant updatedAt = job.map(TacticalScenePreparationJobRepository.Job::updatedAt).orElseGet(Instant::now);
        String message = playerMessage(state);
        boolean mapRequired = stage.mapDefinitionId() != null;
        boolean activationAllowed = state == TacticalPreparationState.READY && scene.readyForActivation();
        return new TacticalPreparationReadModel(sessionId, stage.position(), stage.title(),
                stage.tacticalPreparationRequirement(), current, state, job, scene,
                new TacticalPreparationReadModel.PlayerSafeProjection(state, message, progress, attempts,
                        mapRequired, activationAllowed, updatedAt, preparationProgress),
                new TacticalPreparationReadModel.InternalDiagnostics(job.map(value -> value.status().name()).orElse("ABSENT"),
                        job.map(TacticalScenePreparationJobRepository.Job::failureReason).orElse(null),
                        scene.status().name(), activationAllowed, updatedAt));
    }

    private AdventureSession authorize(SessionId sessionId, OwnerPlayerId owner) {
        AdventureSession session = sessions.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("adventure session not found"));
        if (!session.ownerPlayerId().equals(owner)) throw new SecurityException("adventure session access denied");
        return session;
    }

    private static AdventureStoryPlanStage stage(AdventureStoryPlan plan, int position) {
        return plan.stages().stream().filter(item -> item.position() == position).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("story plan stage not found"));
    }

    private static String playerMessage(TacticalPreparationState state) {
        return switch (state) {
            case NOT_REQUIRED -> "이 단계에는 전술 장면이 필요하지 않습니다.";
            case REQUIRED_PENDING -> "현재 단계가 되면 전술 장면을 준비합니다.";
            case PREPARING -> "전술 장면을 준비하고 있습니다.";
            case READY -> "전술 장면이 준비되었습니다.";
            case FAILED_RETRYABLE -> "전술 장면 준비에 실패했습니다. 다시 시도해 주세요.";
        };
    }

    private static PreparationView player(TacticalPreparationReadModel model) {
        UUID jobId = model.job().map(TacticalScenePreparationJobRepository.Job::jobId).orElse(null);
        var projection = model.player();
        return new PreparationView(jobId, model.sessionId(), model.stagePosition(), model.stageName(), model.state(),
                projection.progress(), projection.attempts(), projection.mapRequired(), projection.message(), null,
                projection.updatedAt(), model.job(), projection.preparationProgress());
    }

    private static SourceCitation citation(AdventurePlanEvidence evidence) {
        return new SourceCitation(evidence.documentType(), evidence.documentId(), evidence.extractionVersion(),
                evidence.locator(), evidence.quote(), evidence.confidence());
    }

    private static String message(Throwable failure) {
        return failure.getMessage() == null || failure.getMessage().isBlank()
                ? failure.getClass().getSimpleName() : failure.getMessage();
    }

    public record PreparationDiagnostics(String jobStatus, String failureReason, String sceneStatus,
            boolean mapActivationAllowed, Instant updatedAt) {}

    /** Player-safe projection; raw failure diagnostics are never copied into failureReason. */
    public record PreparationView(UUID jobId, UUID sessionId, int stagePosition, String stageName,
            TacticalPreparationState state, int progress, int attempts, boolean mapRequired, String message,
            String failureReason, Instant updatedAt, Optional<TacticalScenePreparationJobRepository.Job> job,
            PreparationProgress preparationProgress) {
        public PreparationView(UUID jobId, UUID sessionId, int stagePosition, String stageName,
                TacticalPreparationState state, int progress, int attempts, boolean mapRequired, String message,
                String failureReason, Instant updatedAt, Optional<TacticalScenePreparationJobRepository.Job> job) {
            this(jobId, sessionId, stagePosition, stageName, state, progress, attempts, mapRequired, message,
                    failureReason, updatedAt, job, PreparationProgress.legacy(progress));
        }
        public Status status() {
            return switch (state) {
                case PREPARING -> Status.RUNNING;
                case READY -> Status.COMPLETE;
                case FAILED_RETRYABLE -> Status.FAILED_RETRYABLE;
                case NOT_REQUIRED, REQUIRED_PENDING -> Status.QUEUED;
            };
        }
    }

    public enum Status { QUEUED, RUNNING, COMPLETE, FAILED_RETRYABLE }
}
