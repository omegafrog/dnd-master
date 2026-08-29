package com.dndmaster.gmeval.domain;

import java.util.*;

public record EvalContext(Map<String, Object> worldState, Map<String, Object> playerKnowledgeFacts,
                          List<String> playerKnowledge, String storyStage, Map<String, Object> turnPlan,
                          Map<String, Object> resolvedContext) {
    public EvalContext { worldState = copy(worldState); playerKnowledgeFacts = copy(playerKnowledgeFacts);
        playerKnowledge = List.copyOf(playerKnowledge == null ? List.of() : playerKnowledge);
        if (storyStage == null || storyStage.isBlank()) throw new IllegalArgumentException("storyStage required");
        turnPlan = copy(turnPlan); resolvedContext = copy(resolvedContext); }
    public static EvalContext empty() { return new EvalContext(Map.of(), Map.of(), List.of(), "unknown", null, null); }
    private static <K,V> Map<K,V> copy(Map<K,V> value) { return value == null ? Map.of() : Map.copyOf(value); }
}
