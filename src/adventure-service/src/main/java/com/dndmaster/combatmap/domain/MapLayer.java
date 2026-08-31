package com.dndmaster.combatmap.domain;
import java.util.Objects;
public record MapLayer(String type, String value, LayerVisibility visibility) {
    public MapLayer {
        if (type == null || type.isBlank() || value == null || value.isBlank()) throw new IllegalArgumentException("map layer type and value are required");
        type = type.trim(); value = value.trim(); Objects.requireNonNull(visibility);
    }
}
