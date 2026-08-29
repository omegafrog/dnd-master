package com.dndmaster.adventure.application.runtime;

/** Server-validated binding between one visible GM claim and one evidence key. */
public record GmCitationBinding(String claimText, String outputField, String citationKey) {
    public GmCitationBinding {
        claimText = required(claimText, "claim text");
        outputField = required(outputField, "output field");
        citationKey = required(citationKey, "citation key");
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value.trim();
    }
}
