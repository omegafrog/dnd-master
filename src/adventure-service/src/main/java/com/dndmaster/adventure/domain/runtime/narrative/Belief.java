package com.dndmaster.adventure.domain.runtime.narrative;

public record Belief(String actorId, String subjectId, String assertion, double confidence, String source) {
    public Belief {
        actorId = required(actorId, "belief actor id"); subjectId = required(subjectId, "belief subject id");
        assertion = required(assertion, "belief assertion"); source = required(source, "belief source");
        if (confidence < 0 || confidence > 1) throw new IllegalArgumentException("belief confidence must be between 0 and 1");
    }
    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value.trim();
    }
}
