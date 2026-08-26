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
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Durable, idempotent preparation of exactly the current mapped stage. */
public final class TacticalScenePreparationApplicationService {
    private static final int MAX_ATTEMPTS = 3;
    private final AdventureStoryPlanRepository plans;
    private final AdventureSessionRepository sessions;
    private final AdventureStoryPlanGenerationPort generator;
    private final TacticalScenePlanValidator validator;
    private final TacticalScenePreparationJobRepository jobs;

    public TacticalScenePreparationApplicationService(AdventureStoryPlanRepository plans, AdventureSessionRepository sessions, AdventureStoryPlanGenerationPort generator, TacticalScenePlanValidator validator) {
        this(plans, sessions, generator, validator, new InMemoryTacticalScenePreparationJobRepository());
    }
    public TacticalScenePreparationApplicationService(AdventureStoryPlanRepository plans, AdventureSessionRepository sessions, AdventureStoryPlanGenerationPort generator, TacticalScenePlanValidator validator, TacticalScenePreparationJobRepository jobs) {
        this.plans = Objects.requireNonNull(plans); this.sessions = Objects.requireNonNull(sessions); this.generator = Objects.requireNonNull(generator); this.validator = Objects.requireNonNull(validator); this.jobs = Objects.requireNonNull(jobs);
        for (var job : jobs.findUnfinished()) {
            if (job.status() == TacticalScenePreparationJobRepository.Status.RUNNING) {
                jobs.update(job.jobId(), TacticalScenePreparationJobRepository.Status.QUEUED, job.progress(), job.attempts(), "재접속 후 준비 작업 복원", job.failureReason());
            }
            run(job);
        }
    }
    public PreparationView prepare(SessionId sessionId, OwnerPlayerId owner) { return ensure(sessionId, owner, false); }
    public PreparationView retry(SessionId sessionId, OwnerPlayerId owner) { return ensure(sessionId, owner, true); }
    public PreparationView read(SessionId sessionId, OwnerPlayerId owner, int position) {
        authorize(sessionId, owner);
        return jobs.find(sessionId.value(), position).map(TacticalScenePreparationApplicationService::view).orElseThrow(() -> new IllegalArgumentException("tactical scene preparation not found"));
    }
    private PreparationView ensure(SessionId sessionId, OwnerPlayerId owner, boolean retry) {
        AdventureSession session = authorize(sessionId, owner);
        if (session.status() != AdventureSession.Status.STARTED) throw new IllegalStateException("adventure must be started before tactical preparation");
        AdventureStoryPlan plan = plans.findBySessionId(sessionId).orElseThrow(() -> new IllegalStateException("adventure story plan not found"));
        int position = plan.currentStage() + 1;
        AdventureStoryPlanStage stage = plan.stages().stream().filter(item -> item.position() == position).findFirst().orElseThrow(() -> new IllegalArgumentException("current story plan stage not found"));
        var job = jobs.createOrGet(sessionId.value(), owner.value(), position, stage.title(), stage.mapDefinitionId() != null);
        if (retry && job.status() == TacticalScenePreparationJobRepository.Status.FAILED_RETRYABLE) { jobs.resetForRetry(job.jobId()); job = jobs.find(sessionId.value(), position).orElseThrow(); }
        if (!retry && stage.tacticalScenePlan().readyForActivation() && job.status() == TacticalScenePreparationJobRepository.Status.QUEUED) {
            jobs.update(job.jobId(), TacticalScenePreparationJobRepository.Status.COMPLETE, 100, job.attempts(), "전술 장면 준비 완료", null);
        }
        if (job.status() == TacticalScenePreparationJobRepository.Status.QUEUED || job.status() == TacticalScenePreparationJobRepository.Status.RUNNING) run(job);
        return jobs.find(sessionId.value(), position).map(TacticalScenePreparationApplicationService::view).orElseThrow();
    }
    private void run(TacticalScenePreparationJobRepository.Job job) {
        if (!jobs.claim(job.jobId())) return;
        AdventureSession session = sessions.findById(new SessionId(job.sessionId())).orElseThrow(() -> new IllegalStateException("adventure session not found"));
        AdventureStoryPlan plan = plans.findBySessionId(new SessionId(job.sessionId())).orElseThrow(() -> new IllegalStateException("adventure story plan not found"));
        AdventureStoryPlanStage stage = plan.stages().stream().filter(item -> item.position() == job.stagePosition()).findFirst().orElseThrow();
        if (stage.mapDefinitionId() == null) { jobs.update(job.jobId(), TacticalScenePreparationJobRepository.Status.COMPLETE, 100, 0, "맵이 없는 단계", null); return; }
        List<SourceCitation> citations = stage.evidence().stream().map(TacticalScenePreparationApplicationService::citation).toList();
        var map = new AdventureStoryPlanGenerationPort.MapContext(stage.mapDefinitionId(), stage.mapAssetId(), stage.mapAssetLocator(), stage.mapAssetLocator(), stage.mapConfidence() == null ? 0 : stage.mapConfidence(), stage.mapSafetyStatus(), citations, stage.location());
        List<String> party = session.party().stream().map(member -> member.characterSheetId().value().toString()).toList();
        List<String> violations = List.of();
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            jobs.update(job.jobId(), TacticalScenePreparationJobRepository.Status.RUNNING, Math.min(95, attempt * 25), attempt, "Shard CN 전술 장면 준비 중", null);
            try {
                var request = new TacticalSceneRequest(stage, map, citations, party, violations);
                TacticalScenePlanCandidate candidate = generator.generateTacticalScene(request);
                violations = validator.validate(request, candidate);
                if (violations.isEmpty() && candidate.scene().readyForActivation()) {
                    plans.save(plan.prepareCurrentStage(stage.withTacticalScenePlan(candidate.scene())));
                    jobs.update(job.jobId(), TacticalScenePreparationJobRepository.Status.COMPLETE, 100, attempt, "전술 장면 준비 완료", null);
                    return;
                }
                if (violations.isEmpty()) violations = List.of("tactical scene is absent");
            } catch (RuntimeException failure) { violations = List.of(message(failure)); }
        }
        jobs.update(job.jobId(), TacticalScenePreparationJobRepository.Status.FAILED_RETRYABLE, 100, MAX_ATTEMPTS, "전술 장면 준비에 실패했습니다. 다시 시도해 주세요.", String.join("; ", violations));
    }
    private AdventureSession authorize(SessionId sessionId, OwnerPlayerId owner) { AdventureSession session = sessions.findById(sessionId).orElseThrow(() -> new IllegalArgumentException("adventure session not found")); if (!session.ownerPlayerId().equals(owner)) throw new SecurityException("adventure session access denied"); return session; }
    private static PreparationView view(TacticalScenePreparationJobRepository.Job job) { return new PreparationView(job.jobId(), job.sessionId(), job.stagePosition(), job.stageName(), Status.valueOf(job.status().name()), job.progress(), job.attempts(), job.mapRequired(), job.message(), job.failureReason(), job.updatedAt()); }
    private static SourceCitation citation(AdventurePlanEvidence evidence) { return new SourceCitation(evidence.documentType(), evidence.documentId(), evidence.extractionVersion(), evidence.locator(), evidence.quote(), evidence.confidence()); }
    private static String message(Throwable failure) { return failure.getMessage() == null || failure.getMessage().isBlank() ? failure.getClass().getSimpleName() : failure.getMessage(); }
    public enum Status { QUEUED, RUNNING, COMPLETE, FAILED_RETRYABLE }
    public record PreparationView(UUID jobId, UUID sessionId, int stagePosition, String stageName, Status status, int progress, int attempts, boolean mapRequired, String message, String failureReason, Instant updatedAt) {}
}
