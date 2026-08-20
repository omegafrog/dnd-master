package com.dndmaster.adventure.application.storyplan;

import com.dndmaster.adventure.application.session.AdventureSessionRepository;
import com.dndmaster.adventure.application.runtime.GmTurnRepository;
import com.dndmaster.adventure.domain.runtime.GmTurn;
import com.dndmaster.adventure.domain.runtime.GmTurnStatus;
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
    private final GmTurnRepository gmTurns;

    public FutureTacticalSceneRevisionService(AdventureStoryPlanRepository plans, AdventureSessionRepository sessions) {
        this(plans, sessions, new TacticalScenePlanValidator(), null, null);
    }

    public FutureTacticalSceneRevisionService(AdventureStoryPlanRepository plans, AdventureSessionRepository sessions,
            TacticalScenePlanValidator validator) {
        this(plans, sessions, validator, null, null);
    }

    public FutureTacticalSceneRevisionService(AdventureStoryPlanRepository plans, AdventureSessionRepository sessions,
            AdventureStoryPlanGenerationPort generator) {
        this(plans, sessions, new TacticalScenePlanValidator(), generator, null);
    }

    public FutureTacticalSceneRevisionService(AdventureStoryPlanRepository plans, AdventureSessionRepository sessions,
            TacticalScenePlanValidator validator, AdventureStoryPlanGenerationPort generator) {
        this(plans, sessions, validator, generator, null);
    }

    public FutureTacticalSceneRevisionService(AdventureStoryPlanRepository plans, AdventureSessionRepository sessions,
            TacticalScenePlanValidator validator, AdventureStoryPlanGenerationPort generator, GmTurnRepository gmTurns) {
        this.plans = Objects.requireNonNull(plans);
        this.sessions = Objects.requireNonNull(sessions);
        this.validator = Objects.requireNonNull(validator);
        this.generator = generator;
        this.gmTurns = gmTurns;
    }

    public AdventureStoryPlan revise(SessionId sessionId, OwnerPlayerId owner, int position) {
        return revise(sessionId, owner, position, null);
    }

    public AdventureStoryPlan revise(SessionId sessionId, OwnerPlayerId owner, int position, java.util.UUID causingGmTurnId) {
        if (generator == null) throw new IllegalStateException("future tactical revision requires the grounded generator");
        throw new IllegalArgumentException("causing GM command id is required");
    }

    public AdventureStoryPlan revise(SessionId sessionId, OwnerPlayerId owner, int position,
            java.util.UUID causingGmTurnId, java.util.UUID causingGmCommandId) {
        if (generator == null) throw new IllegalStateException("future tactical revision requires the grounded generator");
        return reviseGenerated(sessionId, owner, position, causingGmTurnId, causingGmCommandId);
    }

    private AdventureStoryPlan reviseGenerated(SessionId sessionId, OwnerPlayerId owner, int position, java.util.UUID causingGmTurnId,
            java.util.UUID causingGmCommandId) {
        AdventureSession session = sessions.findById(sessionId).orElseThrow(() -> new IllegalArgumentException("adventure session not found"));
        if (!session.ownerPlayerId().equals(owner)) throw new SecurityException("adventure session access denied");
        if (session.status() != AdventureSession.Status.STARTED) throw new IllegalStateException("future tactical revision requires a started adventure");
        if (causingGmTurnId == null) throw new IllegalArgumentException("future tactical revision requires causing GM turn id");
        if (causingGmCommandId == null) throw new IllegalArgumentException("future tactical revision requires causing GM command id");
        if (gmTurns == null) throw new IllegalStateException("GM turn repository is required for future tactical revision");
        if (session.startedAdventureId() == null) throw new IllegalArgumentException("adventure is not started");
        GmTurn causingTurn = gmTurns.findByTurnIdAndAdventureId(causingGmTurnId, session.startedAdventureId().value())
                .orElseThrow(() -> new IllegalArgumentException("causing GM turn not found"));
        if (causingTurn.status() != GmTurnStatus.COMMITTED) {
            throw new IllegalArgumentException("causing GM turn must be committed for this adventure");
        }
        if (causingGmCommandId != null && !causingGmCommandId.equals(causingTurn.commandId())) {
            throw new IllegalArgumentException("causing GM command does not match the committed turn");
        }
        if (!(causingTurn.input() instanceof com.dndmaster.adventure.domain.runtime.GmInput.TextInput text)
                || !(text.text().trim().equalsIgnoreCase("revise")
                    || text.text().trim().toLowerCase(java.util.Locale.ROOT).startsWith("revise tactical scene"))) {
            throw new IllegalArgumentException("causing GM turn is not a tactical revision command");
        }
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
        var baseRequest = new TacticalSceneRequest(existing, map, citations, session.party().stream()
                .map(member -> member.characterSheetId().value().toString()).toList(), List.of());
        List<String> violations = List.of();
        TacticalScenePlan scene = null;
        for (int attempt = 1; attempt <= 3; attempt++) {
            TacticalSceneRequest request = baseRequest.withViolations(violations);
            TacticalScenePlanCandidate candidate;
            try {
                candidate = generator.generateTacticalScene(request);
                violations = validator.validate(request, candidate);
                if (violations.isEmpty()) {
                    scene = candidate.scene();
                    break;
                }
            } catch (AdventureStoryPlanCandidateValidationException invalidCandidate) {
                violations = invalidCandidate.violations();
            }
            if (attempt == 3) throw new IllegalArgumentException("tactical scene revision blocked after 3 attempts: " + String.join(", ", violations));
        }
        AdventureStoryPlan revised = current.reviseFutureStage(position, existing.withTacticalScenePlan(scene));
        plans.save(revised, "GM_TURN:" + causingGmTurnId + ":COMMAND:" + causingTurn.commandId() + ":STAGE:" + position);
        return revised;
    }
}
