package com.dndmaster.adventure.application.runtime;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/** Single typed, fail-closed contract for data that may cross into the player/UI boundary. */
public record PlayerProjection(
        String narration,
        String judgment,
        String currentScene,
        List<String> citations,
        List<String> warnings,
        List<String> toolResults) {
    private static final String SAFE_FALLBACK = "공개할 수 있는 장면 정보가 없습니다.";
    private static final Pattern UUID_TEXT = Pattern.compile("(?i)[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}");

    public PlayerProjection {
        narration = required(narration, "narration");
        judgment = required(judgment, "judgment");
        currentScene = required(currentScene, "current scene");
        citations = List.copyOf(Objects.requireNonNull(citations));
        warnings = List.copyOf(Objects.requireNonNull(warnings));
        toolResults = List.copyOf(Objects.requireNonNull(toolResults));
    }

    public static PlayerProjection create(String narration, String judgment, String currentScene,
            List<String> citations, List<String> warnings, List<String> toolResults,
            List<RuntimeEvidence> evidence, Set<String> committedEvents, long gameTurn) {
        throw new IllegalArgumentException("publication scope is required");
    }

    public static PlayerProjection create(String narration, String judgment, String currentScene,
            List<String> citations, List<String> warnings, List<String> toolResults,
            List<RuntimeEvidence> evidence, Set<String> committedEvents, long gameTurn,
            UUID expectedSessionId, UUID expectedScenarioPackageId, UUID expectedOwnerPlayerId) {
        Objects.requireNonNull(expectedSessionId, "expected session id must not be null");
        Objects.requireNonNull(expectedScenarioPackageId, "expected scenario package id must not be null");
        Objects.requireNonNull(expectedOwnerPlayerId, "expected owner player id must not be null");
        Objects.requireNonNull(evidence, "evidence must not be null");
        Objects.requireNonNull(committedEvents, "committed events must not be null");
        if (gameTurn < 0) throw new IllegalArgumentException("game turn must not be negative");
        List<String> protectedValues = new ArrayList<>();
        evidence.stream().filter(item -> item != null)
                .filter(item -> !inScope(item, expectedOwnerPlayerId, expectedSessionId, expectedScenarioPackageId)
                        || !item.visibility().visibleToPlayer(item.disclosureEvent(), item.disclosureTurn(), committedEvents, gameTurn))
                .map(RuntimeEvidence::excerpt).forEach(protectedValues::add);

        String safeNarration = safeText(narration, protectedValues, SAFE_FALLBACK);
        String safeJudgment = safeText(judgment, protectedValues, SAFE_FALLBACK);
        String safeScene = safeText(currentScene, protectedValues, SAFE_FALLBACK);
        Set<String> publicEvidenceRefs = evidence.stream()
                .filter(item -> item != null && inScope(item, expectedOwnerPlayerId, expectedSessionId, expectedScenarioPackageId)
                        && item.visibility().visibleToPlayer(item.disclosureEvent(), item.disclosureTurn(), committedEvents, gameTurn))
                .map(item -> (item.evidenceType().name() + ":" + item.locator()).toUpperCase(Locale.ROOT))
                .collect(java.util.stream.Collectors.toSet());
        List<String> safeCitations = (citations == null ? List.<String>of() : citations).stream()
                .filter(Objects::nonNull)
                .filter(value -> !UUID_TEXT.matcher(value).find())
                .filter(value -> !containsProtected(value, protectedValues))
                .filter(value -> publicEvidenceRefs.contains(value.toUpperCase(Locale.ROOT)))
                .toList();
        List<String> safeWarnings = (warnings == null ? List.<String>of() : warnings).stream()
                .filter(Objects::nonNull)
                .filter(value -> !containsProtected(value, protectedValues))
                .filter(value -> !UUID_TEXT.matcher(value).find())
                .map(value -> value.split(";", 2)[0])
                .filter(value -> !value.isBlank())
                .toList();
        List<String> safeTools = (toolResults == null ? List.<String>of() : toolResults).stream()
                .filter(Objects::nonNull)
                .filter(value -> !containsProtected(value, protectedValues))
                .filter(value -> !UUID_TEXT.matcher(value).find())
                .toList();
        return new PlayerProjection(safeNarration, safeJudgment, safeScene, safeCitations, safeWarnings, safeTools);
    }

    public static String redact(String value, List<RuntimeEvidence> evidence, Set<String> committedEvents,
            long gameTurn, UUID expectedSessionId, UUID expectedScenarioPackageId, UUID expectedOwnerPlayerId) {
        return create(value, value, value, List.of(), List.of(), List.of(), evidence, committedEvents,
                gameTurn, expectedSessionId, expectedScenarioPackageId, expectedOwnerPlayerId).narration();
    }

    public static String redact(String value, Set<String> protectedValues) {
        return safeText(value, new ArrayList<>(Objects.requireNonNull(protectedValues)), SAFE_FALLBACK);
    }

    private static String safeText(String value, List<String> protectedValues, String fallback) {
        String candidate = value == null ? "" : value.trim();
        return candidate.isBlank() || UUID_TEXT.matcher(candidate).find() || containsProtected(candidate, protectedValues) ? fallback : candidate;
    }

    private static boolean inScope(RuntimeEvidence item, UUID expectedOwnerPlayerId, UUID expectedSessionId, UUID expectedScenarioPackageId) {
        return expectedOwnerPlayerId.equals(item.ownerPlayerId())
                && expectedSessionId.equals(item.sessionId())
                && expectedScenarioPackageId.equals(item.scenarioPackageId());
    }

    private static boolean containsProtected(String value, List<String> protectedValues) {
        String normalized = value.toLowerCase(Locale.ROOT);
        return protectedValues.stream().filter(Objects::nonNull).anyMatch(secret -> {
            String hidden = secret.toLowerCase(Locale.ROOT);
            if (hidden.isBlank()) return false;
            if (normalized.contains(hidden)) return true;
            Set<String> tokens = new java.util.HashSet<>(List.of(hidden.split("[^\\p{L}\\p{Nd}]+")));
            tokens.removeIf(token -> token.length() < 2);
            long overlap = tokens.stream().filter(normalized::contains).count();
            return tokens.size() >= 2 && overlap >= 2;
        });
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value.trim();
    }
}
