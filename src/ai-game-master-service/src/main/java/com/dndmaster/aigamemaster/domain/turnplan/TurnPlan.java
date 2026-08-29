package com.dndmaster.aigamemaster.domain.turnplan;

import java.util.List;
import java.util.Objects;

public record TurnPlan(String schemaVersion, String turnId, PlayerIntent playerIntent,
                       List<ResolutionRequest> resolutionRequests, NarrativeIntent narrativeIntent,
                       InformationPolicy informationPolicy, List<StateEffect> stateEffects,
                       StoryProgress storyProgress) {
    public TurnPlan {
        if (!"1".equals(schemaVersion)) throw new IllegalArgumentException("schemaVersion must be 1");
        turnId = required(turnId, "turnId");
        Objects.requireNonNull(playerIntent, "playerIntent");
        resolutionRequests = copy(resolutionRequests, "resolutionRequests");
        Objects.requireNonNull(narrativeIntent, "narrativeIntent");
        Objects.requireNonNull(informationPolicy, "informationPolicy");
        stateEffects = copy(stateEffects, "stateEffects");
        Objects.requireNonNull(storyProgress, "storyProgress");
    }
    static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " required");
        return value.trim();
    }
    static <T> List<T> copy(List<T> value, String name) {
        return List.copyOf(Objects.requireNonNull(value, name));
    }
}
