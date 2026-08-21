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
import com.dndmaster.adventure.domain.adventure.TacticalScenePlan;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Prepares exactly one mapped stage before the stage's map is activated. */
public final class TacticalScenePreparationApplicationService {
    private static final int MAX_ATTEMPTS = 3;
    private final AdventureStoryPlanRepository plans;
    private final AdventureSessionRepository sessions;
    private final AdventureStoryPlanGenerationPort generator;
    private final TacticalScenePlanValidator validator;
    private final ConcurrentHashMap<Key, PreparationView> preparations = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Key, Object> locks = new ConcurrentHashMap<>();

    public TacticalScenePreparationApplicationService(AdventureStoryPlanRepository plans,
            AdventureSessionRepository sessions, AdventureStoryPlanGenerationPort generator,
            TacticalScenePlanValidator validator) {
        this.plans = Objects.requireNonNull(plans, "story plan repository must not be null");
        this.sessions = Objects.requireNonNull(sessions, "session repository must not be null");
        this.generator = Objects.requireNonNull(generator, "tactical scene generator must not be null");
        this.validator = Objects.requireNonNull(validator, "tactical scene validator must not be null");
    }

    public PreparationView prepare(SessionId sessionId, OwnerPlayerId owner) {
        return prepareLocked(sessionId, owner, false);
    }

    public PreparationView retry(SessionId sessionId, OwnerPlayerId owner) {
        return prepareLocked(sessionId, owner, true);
    }

    public PreparationView read(SessionId sessionId, OwnerPlayerId owner, int position) {
        PreparationView view = preparations.get(new Key(sessionId.value(), position));
        if (view == null) throw new IllegalArgumentException("tactical scene preparation not found");
        authorize(sessionId, owner);
        return view;
    }

    private PreparationView prepareLocked(SessionId sessionId, OwnerPlayerId owner, boolean retry) {
        AdventureStoryPlan current = plans.findBySessionId(sessionId)
                .orElseThrow(() -> new IllegalStateException("adventure story plan not found"));
        Key key = new Key(sessionId.value(), current.currentStage() + 1);
        synchronized (locks.computeIfAbsent(key, ignored -> new Object())) {
            return prepareOnce(sessionId, owner, retry);
        }
    }

    private PreparationView prepareOnce(SessionId sessionId, OwnerPlayerId owner, boolean retry) {
        AdventureSession session = authorize(sessionId, owner);
        if (session.status() != AdventureSession.Status.STARTED) {
            throw new IllegalStateException("adventure must be started before tactical preparation");
        }
        AdventureStoryPlan plan = plans.findBySessionId(sessionId)
                .orElseThrow(() -> new IllegalStateException("adventure story plan not found"));
        int position = plan.currentStage() + 1;
        AdventureStoryPlanStage stage = plan.stages().stream().filter(item -> item.position() == position).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("current story plan stage not found"));
        Key key = new Key(sessionId.value(), position);
        if (!retry) {
            PreparationView existing = preparations.get(key);
            if (existing != null) return existing;
            if (stage.tacticalScenePlan().readyForActivation()) {
                PreparationView ready = view(sessionId, stage, Status.READY, 100, 0, "전술 장면 준비 완료", null);
                preparations.putIfAbsent(key, ready);
                return preparations.get(key);
            }
        } else {
            preparations.remove(key);
        }
        if (stage.mapDefinitionId() == null) {
            PreparationView ready = view(sessionId, stage, Status.READY, 100, 0, "맵이 없는 단계", null);
            preparations.put(key, ready);
            return ready;
        }
        List<SourceCitation> citations = stage.evidence().stream().map(TacticalScenePreparationApplicationService::citation).toList();
        var map = new AdventureStoryPlanGenerationPort.MapContext(stage.mapDefinitionId(), stage.mapAssetId(),
                stage.mapAssetLocator(), stage.mapAssetLocator(), stage.mapConfidence() == null ? 0 : stage.mapConfidence(),
                stage.mapSafetyStatus(), citations, stage.location());
        List<String> party = session.party().stream().map(member -> member.characterSheetId().value().toString()).toList();
        List<String> violations = List.of();
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            TacticalSceneRequest request = new TacticalSceneRequest(stage, map, citations, party, violations);
            try {
                TacticalScenePlanCandidate candidate = generator.generateTacticalScene(request);
                violations = validator.validate(request, candidate);
                if (violations.isEmpty() && candidate.scene().readyForActivation()) {
                    AdventureStoryPlan prepared = plan.prepareCurrentStage(stage.withTacticalScenePlan(candidate.scene()));
                    plans.save(prepared);
                    PreparationView ready = view(sessionId, stage, Status.READY, 100, attempt, "전술 장면 준비 완료", null);
                    preparations.put(key, ready);
                    return ready;
                }
                if (violations.isEmpty()) violations = List.of("tactical scene is absent");
            } catch (RuntimeException failure) {
                violations = List.of(message(failure));
            }
        }
        String reason = String.join("; ", violations);
        PreparationView failed = view(sessionId, stage, Status.FAILED_RETRYABLE, 100, MAX_ATTEMPTS,
                "전술 장면 준비에 실패했습니다. 다시 시도해 주세요.", reason);
        preparations.put(key, failed);
        return failed;
    }

    private AdventureSession authorize(SessionId sessionId, OwnerPlayerId owner) {
        AdventureSession session = sessions.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("adventure session not found"));
        if (!session.ownerPlayerId().equals(owner)) throw new SecurityException("adventure session access denied");
        return session;
    }

    private static PreparationView view(SessionId sessionId, AdventureStoryPlanStage stage, Status status,
            int progress, int attempts, String message, String failureReason) {
        return new PreparationView(UUID.nameUUIDFromBytes((sessionId.value() + ":" + stage.position()).getBytes()),
                sessionId.value(), stage.position(), stage.title(), status, progress, attempts, stage.mapDefinitionId() != null,
                message, failureReason, Instant.now());
    }

    private static SourceCitation citation(AdventurePlanEvidence evidence) {
        return new SourceCitation(evidence.documentType(), evidence.documentId(), evidence.extractionVersion(),
                evidence.locator(), evidence.quote(), evidence.confidence());
    }

    private static String message(Throwable failure) {
        return failure.getMessage() == null || failure.getMessage().isBlank() ? failure.getClass().getSimpleName() : failure.getMessage();
    }

    private record Key(UUID sessionId, int position) {}

    public enum Status { PREPARING, READY, FAILED_RETRYABLE }

    public record PreparationView(UUID jobId, UUID sessionId, int stagePosition, String stageName,
            Status status, int progress, int attempts, boolean mapRequired, String message, String failureReason, Instant updatedAt) {}
}
