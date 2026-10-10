package com.dndmaster.adventure.domain.runtime;

import com.dndmaster.adventure.domain.adventure.Adventure;
import com.dndmaster.adventure.domain.runtime.story.StoryRuntimeState;
import java.util.List;
import java.util.Objects;

/** 안전한 턴 반영 전에 보관하는 모험 변경 내용. */
public record PendingRuntimeState(GameStateDelta gameStateDelta, DisclosureState disclosureState,
        CurrentSituation situation, List<RuntimeAddedFact> runtimeAddedFacts, StoryRuntimeState storyRuntimeState) {
    public PendingRuntimeState {
        gameStateDelta = Objects.requireNonNull(gameStateDelta, "game state delta must not be null");
        disclosureState = Objects.requireNonNull(disclosureState, "disclosure state must not be null");
        situation = Objects.requireNonNull(situation, "situation must not be null");
        runtimeAddedFacts = List.copyOf(Objects.requireNonNull(runtimeAddedFacts, "runtime facts must not be null"));
    }

    public PendingRuntimeState(GameStateDelta gameStateDelta, DisclosureState disclosureState,
            CurrentSituation situation, List<RuntimeAddedFact> runtimeAddedFacts) {
        this(gameStateDelta, disclosureState, situation, runtimeAddedFacts, null);
    }

    public static PendingRuntimeState unchanged(Adventure adventure) {
        return new PendingRuntimeState(GameStateDelta.empty(), adventure.disclosureState(), adventure.currentSituation(),
                List.of(), adventure.storyRuntimeState());
    }
}
