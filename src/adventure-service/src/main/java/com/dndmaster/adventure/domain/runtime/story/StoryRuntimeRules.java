package com.dndmaster.adventure.domain.runtime.story;

import com.dndmaster.adventure.domain.scenario.DetailedStage;
import com.dndmaster.adventure.domain.scenario.RevelationDefinition;
import com.dndmaster.adventure.domain.scenario.SituationDefinition;
import com.dndmaster.adventure.domain.scenario.StageBackbone;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Deterministic admission and application of an AI story-runtime proposal. */
public final class StoryRuntimeRules {
    private StoryRuntimeRules() {}

    public static StoryRuntimeState apply(StoryRuntimeState state, DetailedStage stage, StoryRuntimeProposal proposal) {
        Objects.requireNonNull(state, "story runtime state must not be null");
        Objects.requireNonNull(stage, "detailed stage must not be null");
        Objects.requireNonNull(proposal, "story runtime proposal must not be null");
        if (state.processedProposalIds().contains(proposal.proposalId())) return state;
        if (state.version() != proposal.expectedVersion()) {
            throw new IllegalStateException("story runtime version conflict: expected " + proposal.expectedVersion()
                    + " but was " + state.version());
        }
        if (!state.scenarioPackageId().equals(proposal.scenarioPackageId())
                || state.backboneRevision() != proposal.backboneRevision()
                || !state.stageId().equals(proposal.stageId())
                || state.detailedStageRevision() != proposal.detailedStageRevision()) {
            throw new IllegalStateException("story runtime stage reference is stale");
        }
        if (state.lifecycle() != StageLifecycle.ACTIVE) {
            throw new IllegalStateException("stage is no longer active: " + state.lifecycle());
        }

        Map<String, SituationStatus> situations = new LinkedHashMap<>(state.situationStatuses());
        Map<String, RevelationStatus> revelations = new LinkedHashMap<>(state.revelationStatuses());
        Map<String, PressureState> pressures = new LinkedHashMap<>(state.pressureStates());
        String active = state.activeSituationId();
        Set<String> predicates = new java.util.LinkedHashSet<>(state.satisfiedPredicateIds());
        List<PlayCreatedStoryFact> facts = mergeFacts(state.acceptedStoryFacts(), proposal.acceptedStoryFacts());
        validateRevelations(stage, proposal.learnedRevelationIds());
        validatePredicates(stage, proposal.satisfiedPredicateIds());
        for (String revelationId : proposal.learnedRevelationIds()) revelations.put(revelationId, RevelationStatus.LEARNED);
        predicates.addAll(proposal.satisfiedPredicateIds());

        SituationAction action = proposal.situationAction();
        if (action.kind() != SituationAction.Kind.NONE) {
            SituationDefinition situation = stage.situations().stream().filter(candidate -> candidate.situationId().equals(action.situationId()))
                    .findFirst().orElseThrow(() -> new IllegalArgumentException("unknown situation: " + action.situationId()));
            SituationStatus status = situations.get(situation.situationId());
            if (status == null) throw new IllegalArgumentException("situation is not in current pool: " + situation.situationId());
            switch (action.kind()) {
                case ACTIVATE -> {
                    if (active != null) throw new IllegalStateException("a situation is already active");
                    if (status != SituationStatus.AVAILABLE) throw new IllegalStateException("situation is not available");
                    active = situation.situationId();
                }
                case FINISH -> {
                    if (!situation.situationId().equals(active)) throw new IllegalStateException("only active situation can finish");
                    situations.put(situation.situationId(), SituationStatus.USED);
                    active = null;
                }
                case INVALIDATE -> {
                    if (status == SituationStatus.USED) throw new IllegalStateException("used situation cannot be invalidated");
                    situations.put(situation.situationId(), SituationStatus.INVALIDATED);
                    if (situation.situationId().equals(active)) active = null;
                }
                case NONE -> { }
            }
        }

        for (PressureProposal pressureProposal : proposal.pressureProposals()) {
            PressureState pressure = pressures.get(pressureProposal.pressureId());
            if (pressure == null || !stage.pressure().pressureId().equals(pressureProposal.pressureId())) {
                throw new IllegalArgumentException("unknown pressure: " + pressureProposal.pressureId());
            }
            if (pressure.progression() != pressureProposal.expectedProgression()) {
                throw new IllegalStateException("pressure progression is stale");
            }
            if (pressure.status() != PressureStatus.DORMANT && pressure.status() != PressureStatus.ACTIVE) {
                throw new IllegalStateException("pressure is no longer mutable");
            }
            PressureState next = switch (pressureProposal.operation()) {
                case ADVANCE -> pressure.advance();
                case SKIP -> pressure.skip();
                case CANCEL -> pressure.cancel();
                case REPLACE -> pressure.replace(pressureProposal.replacementMaterial());
            };
            pressures.put(pressure.pressureId(), next);
        }
        StageLifecycle lifecycle = state.lifecycle();
        String exitReason = state.exitReason();
        List<String> unresolvedThreats = state.unresolvedThreats();
        List<String> unresolvedConsequences = state.unresolvedConsequenceIds();
        if (proposal.unresolvedExit() != null) {
            if (active != null) {
                situations.put(active, SituationStatus.USED);
                active = null;
            }
            situations.replaceAll((id, status) -> status == SituationStatus.AVAILABLE ? SituationStatus.INVALIDATED : status);
            lifecycle = StageLifecycle.EXITED_UNRESOLVED;
            exitReason = proposal.unresolvedExit().reason();
            unresolvedThreats = List.of(stage.threat().core());
            unresolvedConsequences = List.copyOf(stage.importantConsequenceIds());
        }
        if (situations.equals(state.situationStatuses()) && revelations.equals(state.revelationStatuses())
                && pressures.equals(state.pressureStates()) && Objects.equals(active, state.activeSituationId())
                && predicates.equals(state.satisfiedPredicateIds()) && lifecycle == state.lifecycle()
                && Objects.equals(exitReason, state.exitReason()) && facts.equals(state.acceptedStoryFacts())) {
            return state.withProcessedProposal(proposal.proposalId());
        }
        return state.evolve(situations, revelations, pressures, active, state.openingPresented(), proposal.proposalId(),
                predicates, lifecycle, exitReason, unresolvedThreats, unresolvedConsequences, state.stageHistory(), facts,
                state.premiseInvalidationAudits());
    }

    /** Atomically closes the current stage and starts its immediate next stage. */
    public static StoryRuntimeState transition(StoryRuntimeState state, DetailedStage current, DetailedStage next,
            StageBackbone backbone) {
        return transitionInternal(state, current, next, backbone, true);
    }

    /** Starts the next stage after an explicitly recorded unresolved exit. */
    public static StoryRuntimeState transitionAfterUnresolvedExit(StoryRuntimeState state, DetailedStage current,
            DetailedStage next, StageBackbone backbone) {
        return transitionInternal(state, current, next, backbone, false);
    }

    private static StoryRuntimeState transitionInternal(StoryRuntimeState state, DetailedStage current, DetailedStage next,
            StageBackbone backbone, boolean requireFunnel) {
        Objects.requireNonNull(state, "story runtime state must not be null");
        Objects.requireNonNull(current, "current detailed stage must not be null");
        Objects.requireNonNull(next, "next detailed stage must not be null");
        Objects.requireNonNull(backbone, "stage backbone must not be null");
        validateReference(state, current);
        if (state.lifecycle() != StageLifecycle.ACTIVE
                && !(!requireFunnel && state.lifecycle() == StageLifecycle.EXITED_UNRESOLVED)) {
            throw new IllegalStateException("stage is no longer transitionable: " + state.lifecycle());
        }
        if (requireFunnel) {
            FunnelEvaluation funnel = FunnelEvaluation.evaluate(current, state);
            if (!funnel.satisfied()) throw new IllegalStateException("funnel is not satisfied: "
                    + funnel.missingRevelationIds() + funnel.missingPredicateIds());
        }
        if (!backbone.scenarioPackageId().equals(next.scenarioPackageId())
                || backbone.revision() != next.backboneRevision()) throw new IllegalArgumentException("next stage references another backbone");
        int currentOrder = backbone.stages().stream().filter(entry -> entry.stageId().equals(current.stageId()))
                .mapToInt(entry -> entry.order()).findFirst().orElseThrow(() -> new IllegalArgumentException("current stage is not in backbone"));
        var nextEntry = backbone.stages().stream().filter(entry -> entry.order() == currentOrder + 1).findFirst()
                .orElseThrow(() -> new IllegalStateException("current stage has no next stage"));
        if (!nextEntry.stageId().equals(next.stageId())) throw new IllegalArgumentException("next stage is not immediate successor");

        StageHistoryEntry history = historyOf(state, current, state.lifecycle() == StageLifecycle.EXITED_UNRESOLVED
                ? StageLifecycle.EXITED_UNRESOLVED : StageLifecycle.COMPLETED,
                state.exitReason(), state.unresolvedThreats(), state.unresolvedConsequenceIds());
        StoryRuntimeState started = StoryRuntimeState.start(next);
        return new StoryRuntimeState(state.version() + 1, next.scenarioPackageId(), next.backboneRevision(), next.stageId(),
                next.revision(), started.situationStatuses(), started.revelationStatuses(), started.pressureStates(),
                started.activeSituationId(), started.openingPresented(), state.processedProposalIds(),
                Set.of(), StageLifecycle.ACTIVE, null, state.unresolvedThreats(), state.unresolvedConsequenceIds(),
                append(state.stageHistory(), history), state.acceptedStoryFacts(), state.premiseInvalidationAudits());
    }

    private static StageHistoryEntry historyOf(StoryRuntimeState state, DetailedStage stage, StageLifecycle lifecycle,
            String reason, List<String> unresolvedThreats, List<String> unresolvedConsequences) {
        Set<String> used = state.situationStatuses().entrySet().stream()
                .filter(entry -> entry.getValue() == SituationStatus.USED).map(Map.Entry::getKey)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        return new StageHistoryEntry(stage.stageId(), stage.revision(), lifecycle, used, state.learnedRevelationIds(),
                unresolvedThreats, unresolvedConsequences, reason);
    }

    private static List<StageHistoryEntry> append(List<StageHistoryEntry> history, StageHistoryEntry item) {
        java.util.ArrayList<StageHistoryEntry> result = new java.util.ArrayList<>(history);
        result.add(item);
        return List.copyOf(result);
    }

    private static void validateReference(StoryRuntimeState state, DetailedStage stage) {
        if (!state.scenarioPackageId().equals(stage.scenarioPackageId()) || state.backboneRevision() != stage.backboneRevision()
                || !state.stageId().equals(stage.stageId()) || state.detailedStageRevision() != stage.revision()) {
            throw new IllegalStateException("story runtime stage reference is stale");
        }
    }

    private static void validateRevelations(DetailedStage stage, List<String> ids) {
        Set<String> known = new HashSet<>();
        for (RevelationDefinition revelation : stage.revelations()) known.add(revelation.revelationId());
        for (String id : ids) if (!known.contains(id)) throw new IllegalArgumentException("unknown revelation: " + id);
    }

    private static void validatePredicates(DetailedStage stage, List<String> ids) {
        Set<String> known = new HashSet<>(stage.funnel().requiredPredicateIds());
        for (String id : ids) if (!known.contains(id)) throw new IllegalArgumentException("unknown funnel predicate: " + id);
    }

    private static List<PlayCreatedStoryFact> mergeFacts(List<PlayCreatedStoryFact> current,
            List<PlayCreatedStoryFact> additions) {
        java.util.ArrayList<PlayCreatedStoryFact> merged = new java.util.ArrayList<>(current);
        for (PlayCreatedStoryFact addition : additions) {
            PlayCreatedStoryFact existing = merged.stream().filter(fact -> fact.factId().equals(addition.factId())).findFirst().orElse(null);
            if (existing != null) {
                if (!existing.equals(addition)) throw new IllegalArgumentException("story fact id was reused for different content");
                continue;
            }
            if (merged.stream().anyMatch(fact -> fact.content().equalsIgnoreCase(addition.content()))) continue;
            merged.add(addition);
        }
        return List.copyOf(merged);
    }
}
