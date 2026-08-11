package com.dndmaster.adventure.application.storyplan;

import com.dndmaster.adventure.application.session.AdventureSessionRepository;
import com.dndmaster.adventure.domain.adventure.AdventurePartyMember;
import com.dndmaster.adventure.domain.adventure.AdventureSession;
import com.dndmaster.adventure.domain.adventure.AdventureStoryPlan;
import com.dndmaster.adventure.domain.adventure.AdventureStoryPlanStage;
import com.dndmaster.adventure.domain.adventure.AdventureStoryPlanStatus;
import com.dndmaster.adventure.domain.adventure.AdventurePlanConfiguration;
import com.dndmaster.adventure.domain.adventure.AdventureLength;
import com.dndmaster.adventure.domain.adventure.ControlMode;
import com.dndmaster.adventure.domain.adventure.OwnerPlayerId;
import com.dndmaster.adventure.domain.adventure.SessionId;
import java.util.List;
import java.util.ArrayList;
import java.util.Objects;
import java.util.UUID;
import com.dndmaster.adventure.application.scenario.compilation.ScenarioPackageRepository;

public final class AdventureStoryPlanApplicationService {
    private final AdventureStoryPlanRepository plans;
    private final AdventureSessionRepository sessions;
    private final ScenarioPackageRepository packages;
    private final AdventureStoryPlanGenerationPort generator;

    public AdventureStoryPlanApplicationService(AdventureStoryPlanRepository plans, AdventureSessionRepository sessions) {
        this(plans, sessions, null, request -> defaultStages(request.configuration()));
    }
    public AdventureStoryPlanApplicationService(AdventureStoryPlanRepository plans, AdventureSessionRepository sessions,
            ScenarioPackageRepository packages, AdventureStoryPlanGenerationPort generator) {
        this.plans = Objects.requireNonNull(plans); this.sessions = Objects.requireNonNull(sessions);
        this.packages = packages; this.generator = Objects.requireNonNull(generator);
    }

    public AdventureStoryPlan read(SessionId sessionId, OwnerPlayerId owner) {
        requireSession(sessionId, owner);
        return plans.findBySessionId(sessionId).orElseThrow(() -> new IllegalStateException("adventure story plan not found"));
    }
    public List<AdventureStoryPlan> readHistory(SessionId sessionId, OwnerPlayerId owner) {
        requireSession(sessionId, owner); return plans.readHistory(sessionId);
    }

    public AdventureStoryPlan generate(SessionId sessionId, OwnerPlayerId owner) {
        return generate(sessionId, owner, AdventurePlanConfiguration.defaults());
    }

    public AdventureStoryPlan generate(SessionId sessionId, OwnerPlayerId owner, AdventurePlanConfiguration configuration) {
        AdventureSession session = requireSession(sessionId, owner);
        validateParty(session);
        AdventureStoryPlan previous = plans.findBySessionId(sessionId).orElse(null);
        long version = previous == null ? 1 : previous.version() + 1;
        List<AdventureStoryPlanStage> stages = generator.generate(new AdventureStoryPlanGenerationPort.Request(
                UUID.randomUUID().toString(), session.scenarioPackageRevision(), session.party().size(),
                configuration, sourceDocuments(session), resolutionEvidence(session)));
        AdventureStoryPlan plan = AdventureStoryPlan.ready(
                previous == null ? java.util.UUID.randomUUID() : previous.planId(), session.id(),
                session.scenarioPackageRevision(), session.version(), version, configuration, stages);
        plans.save(plan);
        return plan;
    }

    public AdventureStoryPlan retry(SessionId sessionId, OwnerPlayerId owner) {
        return generate(sessionId, owner);
    }

    public boolean isReadyFor(AdventureSession session) {
        return plans.findBySessionId(session.id())
                .map(plan -> plan.status() == AdventureStoryPlanStatus.READY
                        && plan.packageRevision() == session.scenarioPackageRevision()
                        && plan.partyRevision() == session.version())
                .orElse(false);
    }

    private AdventureSession requireSession(SessionId id, OwnerPlayerId owner) {
        AdventureSession session = sessions.findById(id).orElseThrow(() -> new IllegalArgumentException("adventure session not found"));
        if (!session.ownerPlayerId().equals(owner)) throw new SecurityException("adventure session access denied");
        return session;
    }

    private static void validateParty(AdventureSession session) {
        if (session.status() != AdventureSession.Status.DRAFT) throw new IllegalStateException("story plan requires a draft session");
        if (session.party().size() < session.characterLimit()) throw new IllegalStateException("party must be complete before story plan generation");
        if (session.party().stream().noneMatch(member -> member.controlMode() == ControlMode.DIRECT)) throw new IllegalStateException("story plan requires a direct player character");
    }

    private static List<AdventureStoryPlanStage> defaultStages() {
        return defaultStages(AdventurePlanConfiguration.defaults());
    }

    private static List<AdventureStoryPlanStage> defaultStages(AdventurePlanConfiguration configuration) {
        int stageCount = switch (configuration.adventureLength()) {
            case SHORT -> 3;
            case STANDARD -> 4;
            case LONG -> 7;
        };
        List<AdventureStoryPlanStage> stages = new ArrayList<>();
        for (int position = 1; position <= stageCount; position++) {
            String title = position == 1 ? "Beginning" : position == stageCount ? "Resolution" : position == stageCount - 1 ? "Confrontation" : "Escalation";
            String ending = "ending-" + (((position - 1) % configuration.endingCount()) + 1);
            stages.add(stage(position, title, "Advance the adventure", "Opposition demands a meaningful choice", "The party reaches the next lead", ending));
        }
        return List.copyOf(stages);
    }

    private List<String> sourceDocuments(AdventureSession session) {
        if (packages == null) return List.of("scenario-package:" + session.scenarioPackageId());
        return packages.findById(session.scenarioPackageId()).map(p -> p.documents().stream().map(d -> d.originalFilename()).toList()).orElse(List.of());
    }
    private List<String> resolutionEvidence(AdventureSession session) {
        if (packages == null) return List.of();
        return packages.findById(session.scenarioPackageId()).map(p -> p.units().stream().map(u -> String.valueOf(u.sourceQuote())).filter(s -> !s.equals("null") && !s.isBlank()).limit(20).toList()).orElse(List.of());
    }

    private static AdventureStoryPlanStage stage(int position, String title, String goal, String conflict, String transition, String ending) {
        return new AdventureStoryPlanStage(position, title, goal, conflict, transition, List.of(), List.of(ending));
    }
}
