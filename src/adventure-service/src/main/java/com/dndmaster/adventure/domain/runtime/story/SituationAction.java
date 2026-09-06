package com.dndmaster.adventure.domain.runtime.story;

import java.util.Objects;

public record SituationAction(Kind kind, String situationId) {
    public enum Kind { NONE, ACTIVATE, FINISH, INVALIDATE }

    public SituationAction {
        kind = Objects.requireNonNull(kind, "situation action kind must not be null");
        situationId = situationId == null || situationId.isBlank() ? null : situationId.trim();
        if (kind != Kind.NONE && situationId == null) throw new IllegalArgumentException("situation id is required");
    }

    public static SituationAction none() { return new SituationAction(Kind.NONE, null); }
    public static SituationAction activate(String situationId) { return new SituationAction(Kind.ACTIVATE, situationId); }
    public static SituationAction finish(String situationId) { return new SituationAction(Kind.FINISH, situationId); }
    public static SituationAction invalidate(String situationId) { return new SituationAction(Kind.INVALIDATE, situationId); }
}
