package com.dndmaster.adventure.application.storyplan;

import com.dndmaster.adventure.application.session.AdventureSessionRepository;
import com.dndmaster.adventure.domain.adventure.AdventureSession;
import com.dndmaster.adventure.domain.adventure.AdventureStoryPlan;
import com.dndmaster.adventure.domain.adventure.AdventureStoryPlanStage;
import com.dndmaster.adventure.domain.adventure.OwnerPlayerId;
import com.dndmaster.adventure.domain.adventure.SessionId;
import com.dndmaster.adventure.domain.adventure.TacticalScenePlan;
import java.util.Objects;

/** Post-start command boundary for revising one unrevealed tactical stage. */
public final class FutureTacticalSceneRevisionService {
    private final AdventureStoryPlanRepository plans;
    private final AdventureSessionRepository sessions;

    public FutureTacticalSceneRevisionService(AdventureStoryPlanRepository plans, AdventureSessionRepository sessions) {
        this.plans = Objects.requireNonNull(plans);
        this.sessions = Objects.requireNonNull(sessions);
    }

    public AdventureStoryPlan revise(SessionId sessionId, OwnerPlayerId owner, int position, TacticalScenePlan scene) {
        AdventureSession session = sessions.findById(sessionId).orElseThrow(() -> new IllegalArgumentException("adventure session not found"));
        if (!session.ownerPlayerId().equals(owner)) throw new SecurityException("adventure session access denied");
        if (session.status() != AdventureSession.Status.STARTED) throw new IllegalStateException("future tactical revision requires a started adventure");
        AdventureStoryPlan current = plans.findBySessionId(sessionId).orElseThrow(() -> new IllegalStateException("adventure story plan not found"));
        if (current.status() != com.dndmaster.adventure.domain.adventure.AdventureStoryPlanStatus.READY) throw new IllegalStateException("story plan is not ready");
        AdventureStoryPlanStage existing = current.stages().stream().filter(stage -> stage.position() == position).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("story plan stage not found"));
        AdventureStoryPlan revised = current.reviseFutureStage(position, existing.withTacticalScenePlan(scene));
        plans.save(revised);
        return revised;
    }
}
