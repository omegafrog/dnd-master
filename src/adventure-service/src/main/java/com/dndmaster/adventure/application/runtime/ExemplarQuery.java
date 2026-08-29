package com.dndmaster.adventure.application.runtime;

import java.util.Objects;

public record ExemplarQuery(String scenePurpose, String interactionType, String tone, String pacing,
        String desiredLength, String semanticQuery, int limit) {
    public ExemplarQuery {
        scenePurpose = required(scenePurpose, "scene purpose"); interactionType = required(interactionType, "interaction type");
        tone = required(tone, "tone"); pacing = required(pacing, "pacing"); desiredLength = required(desiredLength, "desired length");
        semanticQuery = required(semanticQuery, "semantic query");
        if (limit <= 0 || limit > 10) throw new IllegalArgumentException("exemplar limit must be between 1 and 10");
    }
    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value.trim();
    }
}
