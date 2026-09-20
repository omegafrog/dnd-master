package com.dndmaster.combatmap.application.spatial;

import com.dndmaster.combatmap.domain.MapId;
import java.util.List;
import java.util.Objects;

public record SpatialRuntimeResult(MapId mapId, long mapVersion, List<String> publicEvents) {
    public SpatialRuntimeResult {
        mapId = Objects.requireNonNull(mapId, "map id must not be null");
        if (mapVersion < 0) throw new IllegalArgumentException("map version must not be negative");
        publicEvents = publicEvents == null ? List.of() : List.copyOf(publicEvents);
    }
}
