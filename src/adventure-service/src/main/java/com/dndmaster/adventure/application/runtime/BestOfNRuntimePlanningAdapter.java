package com.dndmaster.adventure.application.runtime;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Compatibility bridge that applies plan selection before the writer sees a runtime plan. */
public final class BestOfNRuntimePlanningAdapter implements RuntimePlanningPort {
    private static final int MAX_RETRY_COUNT = 2;
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(BestOfNRuntimePlanningAdapter.class);
    private final RuntimePlanningPort delegate;
    private final int requestedCount;
    private final int retryCount;
    private final boolean simpleTurn;
    private final PlanAuditPort audit;

    public BestOfNRuntimePlanningAdapter(RuntimePlanningPort legacy, int requestedCount, boolean simpleTurn,
                                         PlanAuditPort audit) {
        this(legacy, requestedCount, 0, simpleTurn, audit);
    }

    public BestOfNRuntimePlanningAdapter(RuntimePlanningPort legacy, int requestedCount, int retryCount,
                                         boolean simpleTurn, PlanAuditPort audit) {
        this.delegate = java.util.Objects.requireNonNull(legacy);
        if (requestedCount < 1) throw new IllegalArgumentException("requested candidate count must be positive");
        if (retryCount < 0) throw new IllegalArgumentException("retry count must not be negative");
        if (retryCount > MAX_RETRY_COUNT) throw new IllegalArgumentException("retry count must not exceed " + MAX_RETRY_COUNT);
        this.requestedCount = requestedCount;
        this.retryCount = retryCount;
        this.simpleTurn = simpleTurn;
        this.audit = java.util.Objects.requireNonNull(audit);
    }

    @Override
    public RuntimePlan plan(RuntimePlanningRequest request) {
        return planWithOutcomes(request).plan();
    }

    @Override
    public RuntimePlan planNarration(RuntimePlanningRequest request) {
        return delegate.planNarration(request);
    }

    @Override
    public RuntimePlanningResult planWithOutcomes(RuntimePlanningRequest request) {
        int count = PlanningContext.boundedCandidateCount(requestedCount, simpleTurn);
        List<RuntimePlan> plans = new ArrayList<>();
        IllegalStateException lastValidationFailure = null;
        String validationFeedback = "";
        boolean decomposed = delegate instanceof GmAgentRuntimePlanningAdapter;
        for (int i = 0; i < count + retryCount && plans.size() < count; i++) {
            try {
                plans.add(decomposed
                        ? ((GmAgentRuntimePlanningAdapter) delegate).planWithoutTools(request, validationFeedback)
                        : delegate.plan(request));
            } catch (IllegalStateException invalidCandidate) {
                // Candidate generation is best-effort. A single model response
                // that fails the final safety/grounding gate must not discard
                // earlier valid candidates from the same bounded selection.
                if (!decomposed || !isCandidateValidationFailure(invalidCandidate)) throw invalidCandidate;
                lastValidationFailure = invalidCandidate;
                validationFeedback = safeValidationFeedback(invalidCandidate.getMessage());
                LOGGER.warn("gm_plan_regeneration_after_validation turnId={} attempt={} feedback={}",
                        request.turnId(), i + 1, validationFeedback);
            }
        }
        if (plans.isEmpty()) {
            if (lastValidationFailure != null) {
                throw new IllegalStateException("no valid turn plan candidates: " + lastValidationFailure.getMessage(), lastValidationFailure);
            }
            throw new IllegalStateException("no valid turn plan candidates");
        }
        Set<String> knownFacts = request.narrativeContext() == null
                ? Set.of() : request.narrativeContext().factsKnownBy();
        Set<String> allFacts = request.narrativeContext() == null ? Set.of()
                : request.narrativeContext().worldFacts().stream().map(fact -> fact.id()).collect(java.util.stream.Collectors.toSet());
        Set<String> supportedEntities = new java.util.HashSet<>(knownFacts);
        supportedEntities.add(request.currentContext().currentScene());
        request.currentContext().npcStateValue().filter(value -> !value.isBlank()).ifPresent(supportedEntities::add);
        PlanningContext context = new PlanningContext(request.action(),
                request.currentContext().currentScene() + "|" + request.currentContext().latestJudgmentValue().orElse(""),
                request.scenarioContext().isBlank() ? "current" : request.scenarioContext(),
                request.ownerPlayerId().value().toString(), supportedEntities, knownFacts,
                allFacts.stream().filter(fact -> !knownFacts.contains(fact)).collect(java.util.stream.Collectors.toSet()));
        List<PlanCandidate> candidates = new ArrayList<>();
        for (int i = 0; i < plans.size(); i++) {
            RuntimePlan plan = plans.get(i);
            String candidateId = plan.requestedSelectionId().isBlank() ? "runtime-" + i : plan.requestedSelectionId();
            TurnPlan turnPlan = new TurnPlan(plan.scene(), plan.npcState(), plan.judgment(),
                    plan.stateDelta() == null ? List.of() : new ArrayList<>(plan.stateDelta().revealedFactIds()), List.of());
            // Scene and NPC state are narrative prose, not entity identifiers.
            // Treating either generated sentence as an entity rejects otherwise
            // grounded candidates (for example, a newly introduced NPC description).
            Set<String> referencedEntities = Set.of();
            candidates.add(new PlanCandidate(candidateId, turnPlan, request.action(),
                context.stateFingerprint(), context.situationKey(), context.informationBoundary(), referencedEntities,
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
        int selectedIndex = candidates.stream()
                .filter(candidate -> candidate.candidateId().equals(selection.selected().candidateId()))
                .findFirst()
                .map(candidates::indexOf)
                .orElseThrow(() -> new IllegalStateException("selected runtime candidate is not available"));
        RuntimePlan selectedPlan = plans.get(selectedIndex);
        // Materialize tools only after winner selection. The planner regenerates the
        // selected plan during materialization so unsafe candidates never execute tools.
        return decomposed
                ? ((GmAgentRuntimePlanningAdapter) delegate).executeSelectedWithOutcomes(request, selectedPlan, selectedIndex)
                : new RuntimePlanningResult(selectedPlan, List.of());
    }

    private static boolean hasWarning(RuntimePlan plan, String... markers) {
        return plan.warnings().stream().filter(java.util.Objects::nonNull).map(value -> value.toLowerCase(java.util.Locale.ROOT))
                .anyMatch(warning -> java.util.Arrays.stream(markers).anyMatch(warning::contains));
    }

    private static boolean isCandidateValidationFailure(IllegalStateException failure) {
        String message = failure.getMessage();
        return message != null && message.startsWith("GM final validation failed:");
    }

    private static String safeValidationFeedback(String message) {
        if (message == null) return "이전 계획이 최종 검증을 통과하지 못했습니다.";
        String prefix = "GM final validation failed:";
        String feedback = message.startsWith(prefix) ? message.substring(prefix.length()).trim() : message.trim();
        return feedback.length() <= 1600 ? feedback : feedback.substring(0, 1600);
    }
}
