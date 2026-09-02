package com.dndmaster.adventure.application.runtime;

import com.dndmaster.adventure.domain.runtime.narrative.StateDelta;
import com.dndmaster.adventure.domain.runtime.narrative.NarrativeContext;

import java.util.List;

/** The only input allowed across the writer and player response boundary. */
public record PlayerVisibleTurn(String narrationSeed, String currentScene, List<String> visibleFacts,
                                StateDelta stateDelta, NarrativeContext narrativeContext, PlayerRollRequest rollRequest) {
    public PlayerVisibleTurn {
        narrationSeed = narrationSeed == null ? "" : narrationSeed.trim();
        currentScene = currentScene == null ? "" : currentScene.trim();
        visibleFacts = List.copyOf(visibleFacts == null ? List.of() : visibleFacts);
    }

    public PlayerVisibleTurn(String narrationSeed, String currentScene, List<String> visibleFacts,
                             StateDelta stateDelta, NarrativeContext narrativeContext) {
        this(narrationSeed, currentScene, visibleFacts, stateDelta, narrativeContext, null);
    }

    public PlayerVisibleTurn(String narrationSeed, String currentScene, List<String> visibleFacts, StateDelta stateDelta) {
        this(narrationSeed, currentScene, visibleFacts, stateDelta, null);
    }
}
