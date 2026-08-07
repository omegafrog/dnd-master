package com.dndmaster.aigamemaster.api;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** Shared fail-closed checks for provider output that may become player-visible. */
final class GmResponseSafetyPolicy {
    private GmResponseSafetyPolicy() {}

    static void rejectProtectedFacts(String output, Iterable<String> protectedFacts) {
        String normalizedOutput = normalize(output);
        Set<String> outputTokens = tokens(normalizedOutput);
        for (String fact : protectedFacts) {
            if (fact == null || fact.isBlank()) continue;
            String normalizedFact = normalize(fact);
            if (normalizedOutput.contains(normalizedFact)) {
                throw new IllegalArgumentException("GM response contains protected fact");
            }
            Set<String> factTokens = tokens(normalizedFact);
            if (factTokens.size() >= 2 && factTokens.stream().filter(outputTokens::contains).count() >= 2) {
                throw new IllegalArgumentException("GM response paraphrases protected fact");
            }
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{Nd}]+", " ").trim();
    }

    private static Set<String> tokens(String value) {
        Set<String> result = new HashSet<>();
        for (String token : value.split("\\s+")) if (token.length() >= 4) result.add(token);
        return result;
    }
}
