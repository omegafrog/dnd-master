package com.dndmaster.adventure.application.storyplan;

import com.dndmaster.adventure.application.session.AdventureSessionRepository;
import com.dndmaster.adventure.domain.adventure.AdventureSession;
import com.dndmaster.adventure.domain.adventure.AdventureStoryPlan;
import com.dndmaster.adventure.domain.adventure.AdventureStoryPlanStage;
import com.dndmaster.adventure.domain.adventure.OwnerPlayerId;
import com.dndmaster.adventure.domain.adventure.SessionId;
import com.dndmaster.adventure.domain.adventure.TacticalScenePlan;
import java.util.Objects;
import java.util.List;

/** Post-start command boundary for revising one unrevealed tactical stage. */
public final class FutureTacticalSceneRevisionService {
    private final AdventureStoryPlanRepository plans;
    private final AdventureSessionRepository sessions;
    private final TacticalScenePlanValidator validator;
    private final AdventureStoryPlanGenerationPort generator;

    public FutureTacticalSceneRevisionService(AdventureStoryPlanRepository plans, AdventureSessionRepository sessions) {
        this(plans, sessions, new TacticalScenePlanValidator(), null);
    }

    public FutureTacticalSceneRevisionService(AdventureStoryPlanRepository plans, AdventureSessionRepository sessions,
            TacticalScenePlanValidator validator) {
        this(plans, sessions, validator, null);
    }

    public FutureTacticalSceneRevisionService(AdventureStoryPlanRepository plans, AdventureSessionRepository sessions,
            AdventureStoryPlanGenerationPort generator) {
        this(plans, sessions, new TacticalScenePlanValidator(), generator);
    }

    public FutureTacticalSceneRevisionService(AdventureStoryPlanRepository plans, AdventureSessionRepository sessions,
            TacticalScenePlanValidator validator, AdventureStoryPlanGenerationPort generator) {
        this.plans = Objects.requireNonNull(plans);
        this.sessions = Objects.requireNonNull(sessions);
        this.validator = Objects.requireNonNull(validator);
        this.generator = generator;
    }

    public AdventureStoryPlan revise(SessionId sessionId, OwnerPlayerId owner, int position, TacticalScenePlan scene) {
        AdventureSession session = sessions.findById(sessionId).orElseThrow(() -> new IllegalArgumentException("adventure session not found"));
        if (!session.ownerPlayerId().equals(owner)) throw new SecurityException("adventure session access denied");
        if (session.status() != AdventureSession.Status.STARTED) throw new IllegalStateException("future tactical revision requires a started adventure");
        AdventureStoryPlan current = plans.findBySessionId(sessionId).orElseThrow(() -> new IllegalStateException("adventure story plan not found"));
        if (current.status() != com.dndmaster.adventure.domain.adventure.AdventureStoryPlanStatus.READY) throw new IllegalStateException("story plan is not ready");
        AdventureStoryPlanStage existing = current.stages().stream().filter(stage -> stage.position() == position).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("story plan stage not found"));
        if (position <= current.currentStage() + 1) throw new IllegalStateException("current and revealed story stages are immutable");
        if (existing.mapDefinitionId() == null) throw new IllegalArgumentException("future tactical revision requires a tactical map");
        var citations = existing.evidence().stream().map(value -> new AdventureStoryPlanGenerationPort.SourceCitation(
                value.documentType(), value.documentId(), value.extractionVersion(), value.locator(), value.quote(), value.confidence())).toList();
        var map = new AdventureStoryPlanGenerationPort.MapContext(existing.mapDefinitionId(), existing.mapAssetId(), existing.mapAssetLocator(),
                existing.mapAssetLocator(), existing.mapConfidence() == null ? 0 : existing.mapConfidence(), existing.mapSafetyStatus());
        var request = new TacticalSceneRequest(existing, map, citations, List.of());
        if (generator != null) {
            List<String> violations = List.of();
            for (int attempt = 1; attempt <= 3; attempt++) {
                TacticalScenePlanCandidate candidate;
                try {
                    candidate = generator.generateTacticalScene(request);
                    violations = validator.validate(request, candidate);
                    if (violations.isEmpty()) {
                        scene = candidate.scene();
                        break;
                    }
                } catch (RuntimeException failure) {
                    violations = List.of("tactical scene revision generation failed: "
                            + failure.getClass().getSimpleName()
                            + (failure.getMessage() == null || failure.getMessage().isBlank() ? "" : ": " + failure.getMessage()));
                }
                if (attempt == 3) throw new IllegalArgumentException("tactical scene revision blocked after 3 attempts: " + String.join(", ", violations));
            }
        } else {
            var violations = validator.validate(request, TacticalScenePlanCandidate.ready(position, scene, citations));
            if (!violations.isEmpty()) throw new IllegalArgumentException("invalid tactical scene revision: " + String.join(", ", violations));
        }
        AdventureStoryPlan revised = current.reviseFutureStage(position, existing.withTacticalScenePlan(scene));
        plans.save(revised);
        return revised;
    }
}
