package com.dndmaster.adventure.application.runtime;

import com.dndmaster.adventure.domain.runtime.narrative.CharacterKnowledge;
import com.dndmaster.adventure.domain.runtime.narrative.NarrativeState;
import com.dndmaster.adventure.domain.runtime.narrative.StateDelta;
import com.dndmaster.adventure.domain.scenario.ScenarioResolutionDetail;

import java.util.List;
import java.util.Set;

/** Allow-list projection. It never copies judgment, DC, target, comparison or missed clues. */
public final class DeterministicRevealFilter implements RevealFilter {
    @Override
    public PlayerVisibleTurn reveal(NarrativeState state, ResolutionResult resolution, String actorId,
                                    long turn, String scene, String narrationSeed) {
        if (state == null || resolution == null) throw new IllegalArgumentException("reveal input is required");
        var contract = resolution.unit().reveal();
        boolean reveal = contract != null && contract.level() != ScenarioResolutionDetail.RevealLevel.NONE
                && (contract.condition() == ScenarioResolutionDetail.RevealCondition.ALWAYS
                    || contract.condition() == (resolution.success()
                        ? ScenarioResolutionDetail.RevealCondition.ON_SUCCESS
                        : ScenarioResolutionDetail.RevealCondition.ON_FAILURE));
        String factId = contract == null ? null : contract.hiddenFact();
        boolean known = factId != null && state.factsKnownBy(actorId).contains(factId);
        Set<String> ids = reveal && !known && state.worldFacts().containsKey(factId) ? Set.of(factId) : Set.of();
        List<CharacterKnowledge> knowledge = ids.isEmpty() || actorId == null ? List.of()
                : List.of(new CharacterKnowledge(actorId, ids, Set.of()));
        StateDelta delta = new StateDelta(state.version(), Set.of(), ids, knowledge, List.of(),
                state.relationships(), state.activeThreads(), List.of());
        List<String> visible = ids.stream().map(id -> state.worldFacts().get(id).value()).toList();
        return new PlayerVisibleTurn(narrationSeed, scene, visible, delta);
    }
}
