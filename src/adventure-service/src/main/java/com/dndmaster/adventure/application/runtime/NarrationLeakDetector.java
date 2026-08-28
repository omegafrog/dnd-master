package com.dndmaster.adventure.application.runtime;

import java.util.Locale;

/** Detects provider output that appears to reproduce retrieval/source material. */
public final class NarrationLeakDetector {
    private NarrationLeakDetector() {}

    public static boolean isLikelySourceLeak(String narration, EvidencePack evidencePack) {
        if (narration == null || narration.isBlank() || evidencePack == null) return true;
        String normalized = normalize(narration);
        for (RuntimeEvidence evidence : java.util.stream.Stream.of(evidencePack.storybook(), evidencePack.rulebook(), evidencePack.resolution())
                .flatMap(java.util.List::stream).toList()) {
            String excerpt = normalize(evidence.excerpt());
            if (excerpt.length() >= 80 && normalized.contains(excerpt)) return true;
        }
        String lower = normalized.toLowerCase(Locale.ROOT);
        return normalized.length() > 6000 || lower.contains("open game license")
                || (lower.contains("basic rules") && normalized.length() > 1800)
                || lower.contains("chapter 1: step-by-step characters")
                || (lower.contains("contents") && normalized.length() > 2500);
    }

    private static String normalize(String value) { return value == null ? "" : value.replaceAll("\\s+", " ").trim(); }
}
