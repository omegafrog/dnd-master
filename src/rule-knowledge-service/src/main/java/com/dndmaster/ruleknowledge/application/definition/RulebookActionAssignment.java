package com.dndmaster.ruleknowledge.application.definition;

import java.util.List;

public record RulebookActionAssignment(RulebookActionCandidate candidate, List<String> evidenceIds,
        String queryFingerprint, int candidateCount, java.util.Map<String, Object> knownFields, List<String> neededFields,
        List<RulebookActionSharedRule> availableSharedRules) {
    public RulebookActionAssignment(RulebookActionCandidate candidate, List<String> evidenceIds) {
        this(candidate, evidenceIds, "legacy", 0, java.util.Map.of(), List.of(), List.of());
    }
    public RulebookActionAssignment(RulebookActionCandidate candidate, List<String> evidenceIds,
            String queryFingerprint, int candidateCount) {
        this(candidate, evidenceIds, queryFingerprint, candidateCount, java.util.Map.of(), List.of(), List.of());
    }
    public RulebookActionAssignment(RulebookActionCandidate candidate, List<String> evidenceIds,
            String queryFingerprint, int candidateCount, java.util.Map<String, Object> knownFields, List<String> neededFields) {
        this(candidate, evidenceIds, queryFingerprint, candidateCount, knownFields, neededFields, List.of());
    }
    public RulebookActionAssignment {
        if (candidate == null) throw new IllegalArgumentException("assigned candidate is required");
        evidenceIds = evidenceIds == null ? List.of() : List.copyOf(evidenceIds);
        knownFields = knownFields == null ? java.util.Map.of() : java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(knownFields));
        neededFields = neededFields == null ? List.of() : List.copyOf(neededFields);
        availableSharedRules = availableSharedRules == null ? List.of() : List.copyOf(availableSharedRules);
        if (neededFields.stream().anyMatch(value -> value == null || value.isBlank())
                || neededFields.stream().distinct().count() != neededFields.size())
            throw new IllegalArgumentException("assigned needed fields must be unique nonblank names");
        if (queryFingerprint == null || queryFingerprint.isBlank() || candidateCount < 0 || candidateCount > 64)
            throw new IllegalArgumentException("assigned query coverage is invalid");
        if (evidenceIds.isEmpty() || evidenceIds.stream().anyMatch(id -> id == null || id.isBlank()))
            throw new IllegalArgumentException("assigned candidate evidence is required");
    }
}
