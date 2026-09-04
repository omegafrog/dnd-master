package com.dndmaster.adventure.application.runtime;

import java.util.List;

public record RuntimeFactLookupResult(Status status, Source source, String answer,
        List<String> supportingElementIds, List<RuntimeEvidence> evidence) {
    public enum Status { FOUND, NOT_FOUND }
    public enum Source { GAME_STATE, RUNTIME_ADDED_FACT, SCENARIO_MODEL, STORYBOOK_RAG, NONE }

    public RuntimeFactLookupResult {
        status = java.util.Objects.requireNonNull(status, "status must not be null");
        source = java.util.Objects.requireNonNull(source, "source must not be null");
        answer = answer == null ? "" : answer.trim();
        supportingElementIds = supportingElementIds == null ? List.of() : List.copyOf(supportingElementIds);
        evidence = evidence == null ? List.of() : List.copyOf(evidence);
        if (status == Status.FOUND && (source == Source.NONE || answer.isBlank())) {
            throw new IllegalArgumentException("found result requires a source and answer");
        }
        if (status == Status.NOT_FOUND && source != Source.NONE) {
            throw new IllegalArgumentException("not found result must not have a source");
        }
    }

    public static RuntimeFactLookupResult found(Source source, String answer) {
        return new RuntimeFactLookupResult(Status.FOUND, source, answer, List.of(), List.of());
    }

    public static RuntimeFactLookupResult foundScenario(String answer, List<String> ids) {
        return new RuntimeFactLookupResult(Status.FOUND, Source.SCENARIO_MODEL, answer, ids, List.of());
    }

    public static RuntimeFactLookupResult foundRag(String answer, List<RuntimeEvidence> evidence) {
        return new RuntimeFactLookupResult(Status.FOUND, Source.STORYBOOK_RAG, answer, List.of(), evidence);
    }

    public static RuntimeFactLookupResult notFound() {
        return new RuntimeFactLookupResult(Status.NOT_FOUND, Source.NONE, "", List.of(), List.of());
    }
}
