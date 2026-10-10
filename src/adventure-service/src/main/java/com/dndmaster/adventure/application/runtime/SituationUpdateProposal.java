package com.dndmaster.adventure.application.runtime;

import java.util.Objects;

/** Runtime GM proposal for keeping or replacing the current playable problem. */
public record SituationUpdateProposal(Kind kind, String location, String problem, String threat, String goal) {
    public enum Kind { CONTINUE, TRANSITION }

    public SituationUpdateProposal {
        kind = Objects.requireNonNull(kind, "situation update kind must not be null");
        location = optional(location);
        problem = optional(problem);
        threat = optional(threat);
        goal = optional(goal);
    }

    public static SituationUpdateProposal continueSituation(String problem, String threat, String goal) {
        return new SituationUpdateProposal(Kind.CONTINUE, "unknown", problem, threat, goal);
    }

    public static SituationUpdateProposal transition(String location, String problem, String threat, String goal) {
        return new SituationUpdateProposal(Kind.TRANSITION, location, problem, threat, goal);
    }

    private static String optional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
