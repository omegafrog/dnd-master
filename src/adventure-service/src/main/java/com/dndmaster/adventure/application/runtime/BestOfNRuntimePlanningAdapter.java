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
        Set<String> knownFacts = request.narrativeContext() == null
                ? Set.of() : request.narrativeContext().factsKnownBy();
        Set<String> allFacts = request.narrativeContext() == null ? Set.of()
                : request.narrativeContext().worldFacts().stream().map(fact -> fact.id()).collect(java.util.stream.Collectors.toSet());
        Set<String> supportedEntities = new java.util.HashSet<>(knownFacts);
        supportedEntities.add(request.currentContext().currentScene());
        if (!request.currentContext().npcState().isBlank()) supportedEntities.add(request.currentContext().npcState());
        PlanningContext context = new PlanningContext(request.action(),
                request.currentContext().currentScene() + "|" + request.currentContext().latestJudgmentValue().orElse(""),
                request.storyPlanContext().isBlank() ? "current" : request.storyPlanContext(),
                request.ownerPlayerId().value().toString(), supportedEntities, knownFacts,
                allFacts.stream().filter(fact -> !knownFacts.contains(fact)).collect(java.util.stream.Collectors.toSet()));
        List<PlanCandidate> candidates = new ArrayList<>();
        for (int i = 0; i < plans.size(); i++) {
            RuntimePlan plan = plans.get(i);
            String candidateId = plan.selectedBranchId().isBlank() ? "runtime-" + i : plan.selectedBranchId();
            TurnPlan turnPlan = new TurnPlan(plan.scene(), plan.npcState(), plan.judgment(),
                    plan.stateDelta() == null ? List.of() : new ArrayList<>(plan.stateDelta().revealedFactIds()), List.of());
            Set<String> referencedEntities = java.util.stream.Stream.of(plan.scene(), plan.npcState())
                    .filter(value -> value != null && !value.isBlank()).collect(java.util.stream.Collectors.toSet());
            candidates.add(new PlanCandidate(candidateId, turnPlan, request.action(),
                    context.stateFingerprint(), context.storyStage(), context.informationBoundary(), referencedEntities,
                    !hasWarning(plan, "agency", "player_agency", "player agency"),
                    !hasWarning(plan, "continuity"),
                    !hasWarning(plan, "rule", "rules"),
                    Math.max(1, plan.scene().length() + plan.judgment().length())));
        }
        PlanSelection selection = new BestOfNPlanningCoordinator(
                (ignored, ignoredCount) -> candidates,
                (valid, ignored) -> valid.stream().map(candidate -> PlanSelection.score(candidate.candidateId(),
                        0, 0, 0, 0, -candidate.complexity(), List.of())).toList(), audit)
                .plan(context, count, simpleTurn);
        return candidates.stream()
                .filter(candidate -> candidate.candidateId().equals(selection.selected().candidateId()))
                .findFirst()
                .map(selected -> plans.get(candidates.indexOf(selected)))
                .orElseThrow(() -> new IllegalStateException("selected runtime candidate is not available"));
    }

    private static boolean hasWarning(RuntimePlan plan, String... markers) {
        return plan.warnings().stream().filter(java.util.Objects::nonNull).map(value -> value.toLowerCase(java.util.Locale.ROOT))
                .anyMatch(warning -> java.util.Arrays.stream(markers).anyMatch(warning::contains));
    }
}
