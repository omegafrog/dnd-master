package com.dndmaster.combatmap.application.view;

import com.dndmaster.combatmap.domain.GridPosition;
import java.util.Objects;

public record SpawnResolution(GridPosition position, Source source) {
    public SpawnResolution { Objects.requireNonNull(position); Objects.requireNonNull(source); }
    public enum Source { EXPLICIT_TACTICAL, ACTIVATION_CANDIDATE, ENTRY_BOUNDARY, SAFE_FALLBACK }
}
