package com.dndmaster.adventure.application.runtime;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Compatibility bridge that applies plan selection before the writer sees a runtime plan. */
public final class BestOfNRuntimePlanningAdapter implements RuntimePlanningPort {
    private final RuntimePlanningPort legacy;
    private final int requestedCount;
    private final boolean simpleTurn;
    private final PlanAuditPort audit;

    public BestOfNRuntimePlanningAdapter(RuntimePlanningPort legacy, int requestedCount, boolean simpleTurn,
                                         PlanAuditPort audit) {
        this.legacy = java.util.Objects.requireNonNull(legacy);
        if (requestedCount < 1) throw new IllegalArgumentException("requested candidate count must be positive");
        this.requestedCount = requestedCount;
        this.simpleTurn = simpleTurn;
        this.audit = java.util.Objects.requireNonNull(audit);
    }

    @Override
    public RuntimePlan plan(RuntimePlanningRequest request) {
        int count = PlanningContext.boundedCandidateCount(requestedCount, simpleTurn);
        List<RuntimePlan> plans = new ArrayList<>();
        for (int i = 0; i < count; i++) plans.add(legacy.plan(request));
        PlanningContext context = new PlanningContext(request.action(),
                request.currentContext().currentScene() + "|" + request.currentContext().latestJudgmentValue().orElse(""),
                request.storyPlanContext().isBlank() ? "current" : request.storyPlanContext(),
                request.ownerPlayerId().value().toString(), Set.of(), Set.of(), Set.of());
        List<PlanCandidate> candidates = new ArrayList<>();
        for (int i = 0; i < plans.size(); i++) {
            RuntimePlan plan = plans.get(i);
            candidates.add(new PlanCandidate("runtime-" + i, TurnPlan.from(plan), request.action(),
                    context.stateFingerprint(), context.storyStage(), context.informationBoundary(), Set.of(), true, true, true,
                    Math.max(1, plan.scene().length() + plan.judgment().length())));
        }
        PlanSelection selection = new BestOfNPlanningCoordinator(
                (ignored, ignoredCount) -> candidates,
                (valid, ignored) -> valid.stream().map(candidate -> PlanSelection.score(candidate.candidateId(),
                        0, 0, 0, 0, -candidate.complexity(), List.of())).toList(), audit)
                .plan(context, count, simpleTurn);
        return plans.get(Integer.parseInt(selection.selected().candidateId().substring("runtime-".length())));
    }
}
