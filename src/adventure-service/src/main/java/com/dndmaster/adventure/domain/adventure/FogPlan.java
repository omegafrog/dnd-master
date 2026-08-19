package com.dndmaster.adventure.domain.adventure;

import java.util.List;
import java.util.Objects;

public record FogPlan(List<NormalizedCoordinate> hiddenRegions, PlacementGrounding grounding) {
    public FogPlan {
        hiddenRegions = List.copyOf(Objects.requireNonNull(hiddenRegions, "fog regions must be explicit"));
        grounding = Objects.requireNonNull(grounding, "fog grounding must not be null");
    }
}
