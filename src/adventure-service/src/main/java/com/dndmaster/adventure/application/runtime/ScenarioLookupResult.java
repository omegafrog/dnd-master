package com.dndmaster.adventure.application.runtime;

import java.util.List;

public record ScenarioLookupResult(Status status, String answer, List<String> supportingElementIds) {
    public enum Status { FOUND, NOT_FOUND }

    public ScenarioLookupResult {
        status = java.util.Objects.requireNonNull(status, "status must not be null");
        answer = answer == null ? "" : answer.trim();
        supportingElementIds = supportingElementIds == null ? List.of() : List.copyOf(supportingElementIds);
        if (status == Status.FOUND && answer.isBlank()) throw new IllegalArgumentException("found lookup answer must not be blank");
        if (supportingElementIds.stream().anyMatch(id -> id == null || id.isBlank())) {
            throw new IllegalArgumentException("supporting element ids must not be blank");
        }
    }

    public static ScenarioLookupResult found(String answer, List<String> supportingElementIds) {
        return new ScenarioLookupResult(Status.FOUND, answer, supportingElementIds);
    }

    public static ScenarioLookupResult notFound() {
        return new ScenarioLookupResult(Status.NOT_FOUND, "", List.of());
    }
}
