package com.dndmaster.adventure.application.storyplan;

import java.util.Locale;
import java.util.regex.Pattern;

/** Token-boundary support check for claims carried by exact source citations. */
final class SourceClaimSupport {
    private static final Pattern STRUCTURAL_TARGET = Pattern.compile("(?:ending|end)(?:-\\d+)?|stage-\\d+|\\d+");

    private SourceClaimSupport() {}

    static boolean supports(String source, String claim) {
        String normalizedClaim = normalize(claim);
        if (normalizedClaim.isBlank()) return false;
        return (" " + normalize(source) + " ").contains(" " + normalizedClaim + " ");
    }

    static boolean structuralTarget(String value) {
        return STRUCTURAL_TARGET.matcher(normalize(value).replace(' ', '-')).matches();
    }

    static String normalize(String value) {
        if (value == null) return "";
        return value.toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}]+", " ")
                .trim()
                .replaceAll("\\s+", " ");
    }
}
