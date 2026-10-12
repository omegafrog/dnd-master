package com.dndmaster.ruleknowledge.application.definition;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** A compact, source-backed rule extracted only when a missing field required a focused lookup. */
public record RulebookActionSharedRule(String id, String applicability, String conditions,
        String sourceChunkId, String sourceLocator, String sourceEvidence, Map<String, Object> fields) {
    public RulebookActionSharedRule {
        if (id == null || id.isBlank() || !java.util.Set.of("APPLIES", "AMBIGUOUS").contains(applicability)
                || conditions == null || conditions.isBlank() || sourceChunkId == null || sourceChunkId.isBlank()
                || sourceLocator == null || sourceLocator.isBlank() || sourceEvidence == null || sourceEvidence.isBlank())
            throw new IllegalArgumentException("shared rule identity, applicability, conditions, and source are required");
        fields = fields == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(fields));
    }
}
