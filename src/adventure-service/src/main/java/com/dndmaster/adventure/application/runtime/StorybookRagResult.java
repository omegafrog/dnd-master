package com.dndmaster.adventure.application.runtime;

import java.util.List;

public record StorybookRagResult(Status status, String answer, List<RuntimeEvidence> evidence) {
    public enum Status { FOUND, NOT_FOUND }

    public StorybookRagResult {
        status = java.util.Objects.requireNonNull(status, "status must not be null");
        answer = answer == null ? "" : answer.trim();
        evidence = evidence == null ? List.of() : List.copyOf(evidence);
        if (status == Status.FOUND && answer.isBlank()) throw new IllegalArgumentException("found RAG answer must not be blank");
    }

    public static StorybookRagResult found(String answer) {
        return new StorybookRagResult(Status.FOUND, answer, List.of());
    }

    public static StorybookRagResult found(String answer, List<RuntimeEvidence> evidence) {
        return new StorybookRagResult(Status.FOUND, answer, evidence);
    }

    public static StorybookRagResult notFound() {
        return new StorybookRagResult(Status.NOT_FOUND, "", List.of());
    }
}
