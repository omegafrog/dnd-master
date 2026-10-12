package com.dndmaster.ruleknowledge.application.definition;

import java.util.List;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** A rulebook-supported definition with source provenance; nullable values remain unknown until review. */
public record RulebookActionDefinition(String id, String name, String actionKind, String sourceLocator,
        String sourceChunkId, String sourceEvidence, Map<String, Object> fields, List<String> unsupportedFields,
        List<String> sharedRuleRefs) {
    public RulebookActionDefinition(String id, String name, String actionKind, String sourceLocator,
            String sourceChunkId, String sourceEvidence, Map<String, Object> fields, List<String> unsupportedFields) {
        this(id, name, actionKind, sourceLocator, sourceChunkId, sourceEvidence, fields, unsupportedFields, List.of());
    }
    public RulebookActionDefinition {
        if (id == null || id.isBlank() || name == null || name.isBlank() || sourceLocator == null || sourceLocator.isBlank()
                || sourceChunkId == null || sourceChunkId.isBlank() || sourceEvidence == null || sourceEvidence.isBlank())
            throw new IllegalArgumentException("definition identity and source are required");
        // Null is meaningful here: extraction must preserve an unknown value as unknown.
        fields = fields == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(fields));
        unsupportedFields = unsupportedFields == null ? List.of() : List.copyOf(unsupportedFields);
        sharedRuleRefs = sharedRuleRefs == null ? List.of() : List.copyOf(sharedRuleRefs);
    }
}
