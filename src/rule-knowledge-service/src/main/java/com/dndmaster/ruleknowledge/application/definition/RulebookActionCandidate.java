package com.dndmaster.ruleknowledge.application.definition;

public record RulebookActionCandidate(String id, String name, String actionKind, String sourceLocator,
        String sourceChunkId, String sourceEvidence) {
    public RulebookActionCandidate {
        if (id == null || id.isBlank() || name == null || name.isBlank() || actionKind == null || actionKind.isBlank()
                || sourceLocator == null || sourceLocator.isBlank() || sourceChunkId == null || sourceChunkId.isBlank()
                || sourceEvidence == null || sourceEvidence.isBlank()) throw new IllegalArgumentException("action candidate source is required");
    }
}
