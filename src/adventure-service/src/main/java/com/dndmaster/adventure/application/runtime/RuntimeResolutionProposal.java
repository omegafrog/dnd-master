package com.dndmaster.adventure.application.runtime;

import com.dndmaster.adventure.domain.runtime.DisclosureState;
import com.dndmaster.adventure.domain.runtime.GameStateDelta;
import com.dndmaster.adventure.domain.runtime.RuntimeAddedFact;
import java.util.List;
import java.util.Objects;

/** Semantic resolution output kept pending until narration passes safety. */
public record RuntimeResolutionProposal(GameStateDelta gameStateDelta, DisclosureState disclosureState,
        SituationUpdateProposal situationUpdate, List<RuntimeAddedFact> runtimeAddedFacts,
        CompletionProposal completionProposal) {
    public RuntimeResolutionProposal {
        gameStateDelta = Objects.requireNonNull(gameStateDelta, "game state delta must not be null");
        disclosureState = Objects.requireNonNull(disclosureState, "disclosure state must not be null");
        runtimeAddedFacts = List.copyOf(Objects.requireNonNull(runtimeAddedFacts, "runtime facts must not be null"));
        completionProposal = Objects.requireNonNull(completionProposal, "completion proposal must not be null");
    }

    public static RuntimeResolutionProposal unchanged() {
        return new RuntimeResolutionProposal(GameStateDelta.empty(), DisclosureState.empty(), null, List.of(),
                CompletionProposal.continueAdventure());
    }
}
