package com.dndmaster.adventure.application.storyplan;

import com.dndmaster.adventure.domain.adventure.SemanticVerdict;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;

public final class StoryPlanVerdictJson {
    private static final ObjectMapper JSON = new ObjectMapper();
    private StoryPlanVerdictJson() {}
    public static String serialize(List<SemanticVerdict> history) {
        try { return JSON.writeValueAsString(history == null ? List.of() : history); }
        catch (Exception e) { throw new IllegalArgumentException("could not serialize story plan verdict history", e); }
    }
    public static List<SemanticVerdict> deserialize(String json) {
        try { return JSON.readValue(json, new TypeReference<>() {}); }
        catch (Exception e) { throw new IllegalArgumentException("could not deserialize story plan verdict history", e); }
    }
}
