package com.dndmaster.adventure.domain.runtime.story;

import com.dndmaster.adventure.domain.scenario.DetailedStage;
import com.dndmaster.adventure.domain.scenario.RevelationDefinition;
import com.dndmaster.adventure.domain.scenario.SituationDefinition;
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

        Map<String, SituationStatus> situations = new LinkedHashMap<>(state.situationStatuses());
        Map<String, RevelationStatus> revelations = new LinkedHashMap<>(state.revelationStatuses());
        Map<String, PressureState> pressures = new LinkedHashMap<>(state.pressureStates());
        String active = state.activeSituationId();
        validateRevelations(stage, proposal.learnedRevelationIds());
        for (String revelationId : proposal.learnedRevelationIds()) revelations.put(revelationId, RevelationStatus.LEARNED);

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
        if (situations.equals(state.situationStatuses()) && revelations.equals(state.revelationStatuses())
                && pressures.equals(state.pressureStates()) && Objects.equals(active, state.activeSituationId())) {
            return state.withProcessedProposal(proposal.proposalId());
        }
        return state.evolve(situations, revelations, pressures, active, state.openingPresented(), proposal.proposalId());
    }

    private static void validateRevelations(DetailedStage stage, List<String> ids) {
        Set<String> known = new HashSet<>();
        for (RevelationDefinition revelation : stage.revelations()) known.add(revelation.revelationId());
        for (String id : ids) if (!known.contains(id)) throw new IllegalArgumentException("unknown revelation: " + id);
    }
}
