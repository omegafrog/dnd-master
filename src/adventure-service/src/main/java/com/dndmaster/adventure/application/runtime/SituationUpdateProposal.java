package com.dndmaster.adventure.application.runtime;

import java.util.Objects;

/** Runtime GM proposal for keeping or replacing the current playable problem. */
public record SituationUpdateProposal(Kind kind, String location, String problem, String threat, String goal) {
    public enum Kind { CONTINUE, TRANSITION }

    public SituationUpdateProposal {
        kind = Objects.requireNonNull(kind, "situation update kind must not be null");
        location = required(location, "situation location");
        problem = required(problem, "situation problem");
        threat = required(threat, "situation threat");
        goal = required(goal, "situation goal");
    }

    public static SituationUpdateProposal continueSituation(String problem, String threat, String goal) {
        return new SituationUpdateProposal(Kind.CONTINUE, "unknown", problem, threat, goal);
    }

    public static SituationUpdateProposal transition(String location, String problem, String threat, String goal) {
        return new SituationUpdateProposal(Kind.TRANSITION, location, problem, threat, goal);
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value.trim();
    }
}
