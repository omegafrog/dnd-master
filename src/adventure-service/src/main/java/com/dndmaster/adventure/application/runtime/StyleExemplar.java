package com.dndmaster.adventure.application.runtime;

import java.util.Objects;

/** Curated presentation guidance; never a source of world facts or state changes. */
public record StyleExemplar(String id, String text, String scenePurpose, String interactionType,
        String tone, String pacing, String desiredLength, Provenance provenance, boolean generic) {
    public StyleExemplar {
        id = required(id, "id"); text = required(text, "text");
        scenePurpose = required(scenePurpose, "scene purpose"); interactionType = required(interactionType, "interaction type");
        tone = required(tone, "tone"); pacing = required(pacing, "pacing"); desiredLength = required(desiredLength, "desired length");
        provenance = Objects.requireNonNull(provenance, "provenance must not be null");
    }
    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value.trim();
    }
}
