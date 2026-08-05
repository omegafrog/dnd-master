package com.dndmaster.adventure.domain.scenario;

import java.util.Objects;
import java.util.UUID;

public record StoryMapBinding(String stage, String location, String entryCondition, UUID mapDefinitionId) {
    public StoryMapBinding {
        stage = required(stage, "stage"); location = required(location, "location"); entryCondition = required(entryCondition, "entry condition");
        mapDefinitionId = Objects.requireNonNull(mapDefinitionId, "map definition id must not be null");
        if (entryCondition.matches("(?i).*\\b(?:turn|round)\\s*[-#]?\\d+\\b.*")) throw new IllegalArgumentException("fixed turn-number binding is not allowed");
    }
    private static String required(String value, String name) { if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank"); return value.trim(); }
    public boolean matches(String currentStage, String currentLocation, String satisfiedCondition) {
        return stage.equalsIgnoreCase(currentStage) && location.equalsIgnoreCase(currentLocation) && entryCondition.equalsIgnoreCase(satisfiedCondition);
    }
}
