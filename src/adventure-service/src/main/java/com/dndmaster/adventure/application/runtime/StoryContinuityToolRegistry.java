package com.dndmaster.adventure.application.runtime;

import java.util.Set;

/** Domain-owned continuity tools. Handlers must perform local validation and idempotent persistence. */
public final class StoryContinuityToolRegistry {
    private StoryContinuityToolRegistry() {}

    public static Set<GmToolDefinition> definitions(OfficialToolPort reviseStoryPlan, OfficialToolPort advanceGameTime) {
        String reviseSchema = "{\"type\":\"object\",\"required\":[\"sessionId\",\"turnId\",\"candidateStages\",\"expectedPlanVersion\"],\"properties\":{\"sessionId\":{\"type\":\"string\"},\"turnId\":{\"type\":\"string\"},\"candidateStages\":{\"type\":\"array\"},\"expectedPlanVersion\":{\"type\":\"integer\"}}}";
        String timeSchema = "{\"type\":\"object\",\"required\":[\"sessionId\",\"turnId\",\"turns\",\"expectedClockVersion\"],\"properties\":{\"sessionId\":{\"type\":\"string\"},\"turnId\":{\"type\":\"string\"},\"turns\":{\"type\":\"integer\"},\"expectedClockVersion\":{\"type\":\"integer\"}}}";
        return Set.of(GmToolDefinition.fromOfficial("revise_story_plan", reviseSchema, reviseStoryPlan),
                GmToolDefinition.fromOfficial("advance_game_time", timeSchema, advanceGameTime));
    }
}
