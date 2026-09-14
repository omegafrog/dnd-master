package com.dndmaster.combatmap.application.view;

import com.dndmaster.combatmap.domain.GridPosition;
import java.util.Objects;

public record SpawnResolution(GridPosition position, Source source) {
    public SpawnResolution { Objects.requireNonNull(position); Objects.requireNonNull(source); }
    public enum Source { EXPLICIT_TACTICAL, USER_CONFIRMED, ACTIVATION_CANDIDATE, AGENT_PROPOSAL }
}
