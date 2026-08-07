package com.dndmaster.adventure.application.storyplan;

import com.dndmaster.adventure.application.session.AdventureSessionRepository;
import com.dndmaster.adventure.domain.adventure.AdventurePartyMember;
import com.dndmaster.adventure.domain.adventure.AdventureSession;
import com.dndmaster.adventure.domain.adventure.AdventureStoryPlan;
import com.dndmaster.adventure.domain.adventure.AdventureStoryPlanStage;
import com.dndmaster.adventure.domain.adventure.AdventureStoryPlanStatus;
import com.dndmaster.adventure.domain.adventure.ControlMode;
import com.dndmaster.adventure.domain.adventure.OwnerPlayerId;
import com.dndmaster.adventure.domain.adventure.SessionId;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import com.dndmaster.adventure.application.scenario.compilation.ScenarioPackageRepository;
import com.dndmaster.adventure.application.runtime.GmProviderBindingRepository;

public final class AdventureStoryPlanApplicationService {
    private final AdventureStoryPlanRepository plans;
    private final AdventureSessionRepository sessions;
    private final ScenarioPackageRepository packages;
    private final AdventureStoryPlanGenerationPort generator;
    private final GmProviderBindingRepository providerBindings;
    private final ConcurrentHashMap<UUID, Boolean> running = new ConcurrentHashMap<>();
    private final ExecutorService generationExecutor = Executors.newFixedThreadPool(2, new StoryPlanThreadFactory());

    public AdventureStoryPlanApplicationService(AdventureStoryPlanRepository plans, AdventureSessionRepository sessions) {
        this(plans, sessions, null, request -> defaultStages());
    }
    public AdventureStoryPlanApplicationService(AdventureStoryPlanRepository plans, AdventureSessionRepository sessions,
            ScenarioPackageRepository packages, AdventureStoryPlanGenerationPort generator) {
        this(plans, sessions, packages, generator, null);
    }
    public AdventureStoryPlanApplicationService(AdventureStoryPlanRepository plans, AdventureSessionRepository sessions,
            ScenarioPackageRepository packages, AdventureStoryPlanGenerationPort generator, GmProviderBindingRepository providerBindings) {
        this.plans = Objects.requireNonNull(plans); this.sessions = Objects.requireNonNull(sessions);
        this.packages = packages; this.generator = Objects.requireNonNull(generator);
        this.providerBindings = providerBindings;
    }

    public AdventureStoryPlan read(SessionId sessionId, OwnerPlayerId owner) {
        requireSession(sessionId, owner);
        return plans.findBySessionId(sessionId).orElseThrow(() -> new IllegalStateException("adventure story plan not found"));
    }
    public List<AdventureStoryPlan> readHistory(SessionId sessionId, OwnerPlayerId owner) {
        requireSession(sessionId, owner); return plans.readHistory(sessionId);
    }

    public AdventureStoryPlan generate(SessionId sessionId, OwnerPlayerId owner) {
        return generate(sessionId, owner, true);
    }

    private AdventureStoryPlan generate(SessionId sessionId, OwnerPlayerId owner, boolean rejectIfAlreadyRunning) {
        AdventureSession session = requireSession(sessionId, owner);
        validateParty(session);
        if (rejectIfAlreadyRunning && running.containsKey(sessionId.value())) {
            return plans.findBySessionId(sessionId).orElseThrow();
        }
        AdventureStoryPlan previous = plans.findBySessionId(sessionId).orElse(null);
        long version = previous == null ? 1 : previous.version() + 1;
        com.dndmaster.adventure.application.runtime.GmProviderSelection provider = providerBindings == null ? null
                : providerBindings.current(sessionId.value()).map(binding -> binding.selection()).orElse(null);
        List<AdventureStoryPlanStage> stages = generator.generate(new AdventureStoryPlanGenerationPort.Request(
                UUID.randomUUID().toString(), session.scenarioPackageRevision(), session.party().size(),
                sourceDocuments(session), resolutionEvidence(session),
                provider == null ? null : provider.provider(),
                provider == null ? null : provider.model(),
                provider == null ? null : provider.reasoning()));
        AdventureStoryPlan plan = AdventureStoryPlan.ready(
                previous == null ? java.util.UUID.randomUUID() : previous.planId(),
                session.id(), session.scenarioPackageRevision(), session.version(), version, stages);
        plans.save(plan);
        return plan;
    }

    public AdventureStoryPlan startGeneration(SessionId sessionId, OwnerPlayerId owner) {
        AdventureSession session = requireSession(sessionId, owner);
        validateParty(session);
        AdventureStoryPlan previous = plans.findBySessionId(sessionId).orElse(null);
        long version = previous == null ? 1 : previous.version() + 1;
        AdventureStoryPlan generating = AdventureStoryPlan.generating(
                previous == null ? UUID.randomUUID() : previous.planId(), session.id(),
                session.scenarioPackageRevision(), session.version(), version);
        plans.save(generating);
        if (running.putIfAbsent(sessionId.value(), Boolean.TRUE) == null) {
            java.util.concurrent.CompletableFuture.runAsync(() -> {
                try {
                    generate(sessionId, owner, false);
                } catch (RuntimeException failure) {
                    plans.save(AdventureStoryPlan.failed(generating.planId(), generating.sessionId(),
                            generating.packageRevision(), generating.partyRevision(), generating.version(),
                            "story plan provider unavailable"));
                } finally {
                    running.remove(sessionId.value());
                }
            }, generationExecutor);
        }
        return generating;
    }

    private static final class StoryPlanThreadFactory implements ThreadFactory {
        private int sequence;

        @Override
        public synchronized Thread newThread(Runnable task) {
            Thread thread = new Thread(task, "story-plan-generation-" + (++sequence));
            thread.setDaemon(true);
            return thread;
        }
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
        return List.of(
                stage(1, "Beginning", "Discover the adventure premise", "An urgent problem demands action", "The party accepts a concrete lead"),
                stage(2, "Escalation", "Pursue the lead", "Opposition reveals a larger threat", "The party obtains decisive evidence"),
                stage(3, "Confrontation", "Choose how to resolve the threat", "The final obstacle tests the party", "The threat is defeated, transformed, or survives"),
                stage(4, "Resolution", "Bring the journey to a meaningful close", "Consequences reshape the party's situation", "A planned ending is reached"));
    }

    private List<String> sourceDocuments(AdventureSession session) {
        if (packages == null) return List.of("scenario-package:" + session.scenarioPackageId());
        return packages.findById(session.scenarioPackageId()).map(p -> p.documents().stream().map(d -> d.originalFilename()).toList()).orElse(List.of());
    }
    private List<String> resolutionEvidence(AdventureSession session) {
        if (packages == null) return List.of();
        return packages.findById(session.scenarioPackageId()).map(p -> p.units().stream().map(u -> String.valueOf(u.sourceQuote())).filter(s -> !s.equals("null") && !s.isBlank()).limit(20).toList()).orElse(List.of());
    }

    private static AdventureStoryPlanStage stage(int position, String title, String goal, String conflict, String transition) {
        return new AdventureStoryPlanStage(position, title, goal, conflict, transition, List.of(), List.of("ending-" + position));
    }
}
