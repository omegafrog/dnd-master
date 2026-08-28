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
import com.dndmaster.adventure.domain.adventure.FogPlan;
import com.dndmaster.adventure.domain.adventure.NormalizedCoordinate;
import com.dndmaster.adventure.domain.adventure.PlacementGrounding;
import com.dndmaster.adventure.domain.adventure.TacticalPlacement;
import com.dndmaster.adventure.domain.adventure.TacticalPlacementKind;
import com.dndmaster.adventure.domain.adventure.TacticalSceneBoundary;
import com.dndmaster.adventure.domain.adventure.TacticalScenePlan;
import com.dndmaster.adventure.domain.adventure.TacticalScenePlanStatus;
import com.dndmaster.adventure.domain.adventure.TacticalTrigger;
import com.dndmaster.adventure.domain.adventure.TacticalTriggerType;
import com.dndmaster.adventure.domain.adventure.TacticalOutcome;
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
        // A previous live request may have exhausted AI attempts before the bounded fallback existed.
        // Re-queue such a safe job on an ordinary prepare call so stale failures can self-heal.
        if (!retry && job.status() == TacticalScenePreparationJobRepository.Status.FAILED_RETRYABLE
                && fallbackEligible(stage, session)) {
            jobs.resetForRetry(job.jobId());
            job = jobs.find(sessionId.value(), position).orElseThrow();
        }
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
        TacticalScenePlan fallback = deterministicFallback(stage, session);
        if (fallback != null) {
            plans.save(plan.prepareCurrentStage(stage.withTacticalScenePlan(fallback)));
            jobs.update(job.jobId(), TacticalScenePreparationJobRepository.Status.COMPLETE, 100, MAX_ATTEMPTS,
                    "AI 전술 장면 준비 실패로 안전한 최소 전술 장면을 사용했습니다", String.join("; ", violations));
            return;
        }
        jobs.update(job.jobId(), TacticalScenePreparationJobRepository.Status.FAILED_RETRYABLE, 100, MAX_ATTEMPTS, "전술 장면 준비에 실패했습니다. 다시 시도해 주세요.", String.join("; ", violations));
    }

    /** A bounded escape hatch for a safe map: only party identity is known, so nothing else is invented. */
    private static TacticalScenePlan deterministicFallback(AdventureStoryPlanStage stage, AdventureSession session) {
        if (!fallbackEligible(stage, session)) return null;
        TacticalSceneBoundary boundary = new TacticalSceneBoundary(new NormalizedCoordinate(0, 0),
                new NormalizedCoordinate(1, 1), List.of());
        PlacementGrounding grounding = PlacementGrounding.aiInference(
                "bounded fallback: party identity and map-safe stage boundary only; no tactical entities inferred");
        List<TacticalPlacement> players = new java.util.ArrayList<>();
        for (int index = 0; index < session.party().size(); index++) {
            double x = .1 + (index % 8) * .1;
            double y = .1 + (index / 8) * .1;
            players.add(new TacticalPlacement("player-" + session.party().get(index).characterSheetId().value(),
                    TacticalPlacementKind.PLAYER, new NormalizedCoordinate(x, y), grounding));
        }
        List<TacticalPlacement> enemies = new java.util.ArrayList<>();
        for (int index = 0; index < stage.enemies().size(); index++) {
            enemies.add(new TacticalPlacement("enemy-" + index + "-" + stage.enemies().get(index), TacticalPlacementKind.ENEMY,
                    new NormalizedCoordinate(.8 - (index % 6) * .1, .8 - (index / 6) * .1), grounding));
        }
        // Keep the fallback executable: a ready scene with no authored trigger
        // makes the first interaction fail in the client with no selectable action.
        List<String> entryTargets = new java.util.ArrayList<>();
        entryTargets.add(players.get(0).id()); entryTargets.addAll(enemies.stream().map(TacticalPlacement::id).toList());
        TacticalTrigger entry = new TacticalTrigger("fallback-entry", TacticalTriggerType.COMBAT_ENTRY,
                entryTargets, "", grounding, "enter the area");
        TacticalOutcome outcome = new TacticalOutcome("fallback-entry-outcome", "the party enters the area", grounding);
        return new TacticalScenePlan(TacticalScenePlan.CURRENT_SCHEMA_VERSION, TacticalScenePlanStatus.READY,
                boundary, players, List.of(), List.of(), enemies, List.of(), List.of(), List.of(),
                new FogPlan(List.of(), grounding), List.of(entry), List.of(outcome), List.of());
    }

    private static boolean fallbackEligible(AdventureStoryPlanStage stage, AdventureSession session) {
        return stage.mapDefinitionId() != null && "SAFE".equalsIgnoreCase(stage.mapSafetyStatus())
                && stage.mapConfidence() != null && stage.mapConfidence() >= .8 && !session.party().isEmpty();
    }
    private AdventureSession authorize(SessionId sessionId, OwnerPlayerId owner) { AdventureSession session = sessions.findById(sessionId).orElseThrow(() -> new IllegalArgumentException("adventure session not found")); if (!session.ownerPlayerId().equals(owner)) throw new SecurityException("adventure session access denied"); return session; }
    private static PreparationView view(TacticalScenePreparationJobRepository.Job job) { return new PreparationView(job.jobId(), job.sessionId(), job.stagePosition(), job.stageName(), Status.valueOf(job.status().name()), job.progress(), job.attempts(), job.mapRequired(), job.message(), job.failureReason(), job.updatedAt()); }
    private static SourceCitation citation(AdventurePlanEvidence evidence) { return new SourceCitation(evidence.documentType(), evidence.documentId(), evidence.extractionVersion(), evidence.locator(), evidence.quote(), evidence.confidence()); }
    private static String message(Throwable failure) { return failure.getMessage() == null || failure.getMessage().isBlank() ? failure.getClass().getSimpleName() : failure.getMessage(); }
    public enum Status { QUEUED, RUNNING, COMPLETE, FAILED_RETRYABLE }
    public record PreparationView(UUID jobId, UUID sessionId, int stagePosition, String stageName, Status status, int progress, int attempts, boolean mapRequired, String message, String failureReason, Instant updatedAt) {}
}
