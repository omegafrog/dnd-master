package com.dndmaster.adventure.application.runtime;

import com.dndmaster.adventure.domain.runtime.CurrentSituation;
import com.dndmaster.adventure.domain.runtime.DisclosureState;
import com.dndmaster.adventure.domain.runtime.GameStateDelta;
import com.dndmaster.adventure.domain.runtime.RuntimeAddedFact;
import java.util.List;
import java.util.Objects;

/** Complete uncommitted Adventure changes carried by a fixed RuntimeTurn. */
public record PendingRuntimeState(GameStateDelta gameStateDelta, DisclosureState disclosureState,
        CurrentSituation situation, List<RuntimeAddedFact> runtimeAddedFacts) {
    public PendingRuntimeState {
        gameStateDelta = Objects.requireNonNull(gameStateDelta, "game state delta must not be null");
        disclosureState = Objects.requireNonNull(disclosureState, "disclosure state must not be null");
        situation = Objects.requireNonNull(situation, "situation must not be null");
        runtimeAddedFacts = List.copyOf(Objects.requireNonNull(runtimeAddedFacts, "runtime facts must not be null"));
    }

    public static PendingRuntimeState unchanged(com.dndmaster.adventure.domain.adventure.Adventure adventure) {
        return new PendingRuntimeState(GameStateDelta.empty(), adventure.disclosureState(), adventure.currentSituation(), List.of());
    }
}
