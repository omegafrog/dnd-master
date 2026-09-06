package com.dndmaster.adventure.domain.scenario;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/** A source-pinned combat situation that may be activated by the runtime GM. */
public record CombatScenarioDefinition(
        String scenarioId,
        String enemyKey,
        String displayName,
        int count,
        String location,
        List<ScenarioSourceReference> sourceRefs) {
    public CombatScenarioDefinition {
        scenarioId = required(scenarioId, "combat scenario id");
        enemyKey = required(enemyKey, "combat scenario enemy key");
        displayName = required(displayName, "combat scenario display name");
        location = location == null || location.isBlank() ? "unknown" : location.trim();
        sourceRefs = List.copyOf(Objects.requireNonNull(sourceRefs, "combat scenario source refs must not be null"));
        if (sourceRefs.isEmpty()) throw new IllegalArgumentException("combat scenario source refs must not be empty");
        if (count < 1) throw new IllegalArgumentException("combat scenario count must be positive");
    }

    public static java.util.Optional<CombatScenarioDefinition> fromElement(ScenarioModelElement element) {
        if (element == null || !"combat-scenario".equalsIgnoreCase(element.type())
                && !"combat_scenario".equalsIgnoreCase(element.type())) return java.util.Optional.empty();
        Map<String, Object> attributes = element.attributes();
        String enemyKey = string(attributes, "enemyKey");
        String displayName = string(attributes, "displayName");
        if (enemyKey.isBlank() || displayName.isBlank()) return java.util.Optional.empty();
        int count = integer(attributes.get("count"), 1);
        if (element.sourceRefs().isEmpty()) return java.util.Optional.empty();
        return java.util.Optional.of(new CombatScenarioDefinition(element.elementId(), enemyKey, displayName, count,
                string(attributes, "location"), element.sourceRefs()));
    }

    private static String string(Map<String, Object> attributes, String key) {
        Object value = attributes.get(key);
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static int integer(Object value, int fallback) {
        if (value instanceof Number number) return number.intValue();
        try {
            return value == null ? fallback : Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value.trim();
    }
}
