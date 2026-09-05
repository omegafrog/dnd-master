package com.dndmaster.adventure.domain.scenario;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/** A typed, source-pinned element of the hidden ScenarioModel document. */
public record ScenarioModelElement(
        String elementId,
        String type,
        Map<String, Object> attributes,
        List<ScenarioSourceReference> sourceRefs) {
    public ScenarioModelElement {
        if (elementId == null || elementId.isBlank()) throw new IllegalArgumentException("element id is required");
        if (type == null || type.isBlank()) throw new IllegalArgumentException("element type is required");
        attributes = Map.copyOf(Objects.requireNonNull(attributes, "attributes are required"));
        sourceRefs = List.copyOf(Objects.requireNonNull(sourceRefs, "source refs are required"));
    }
}
