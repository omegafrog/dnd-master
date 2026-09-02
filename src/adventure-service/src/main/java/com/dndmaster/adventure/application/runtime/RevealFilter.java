package com.dndmaster.adventure.application.runtime;

import com.dndmaster.adventure.domain.runtime.narrative.NarrativeState;

public interface RevealFilter {
    PlayerVisibleTurn reveal(NarrativeState state, ResolutionResult resolution, String actorId,
                             long turn, String scene, String narrationSeed);
}
