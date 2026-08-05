package com.dndmaster.adventure.application.runtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.OptionalInt;
import java.util.UUID;

public final class ContinuityToolHandlers {
    private ContinuityToolHandlers() {}
    public static OfficialToolPort revise(ObjectMapper mapper, StoryContinuityCommandService service) {
        return invocation -> {
            try {
                JsonNode a = mapper.readTree(invocation.argumentsJson());
                ContinuityCommandResult result = service.revise(UUID.fromString(a.get("sessionId").asText()), UUID.fromString(a.get("commandId").asText()), UUID.fromString(a.get("turnId").asText()),
                        mapper.convertValue(a.get("candidateStages"), mapper.getTypeFactory().constructCollectionType(List.class, String.class)), a.get("expectedPlanVersion").asLong());
                return GmToolOutcome.completed(result.value(), result.version(), result.reference());
            } catch (Exception e) { throw new ToolArgumentInvalidException("invalid revise_story_plan arguments"); }
        };
    }
    public static OfficialToolPort advance(ObjectMapper mapper, StoryContinuityCommandService service) {
        return invocation -> {
            try {
                JsonNode a = mapper.readTree(invocation.argumentsJson());
                ContinuityCommandResult result = service.advance(UUID.fromString(a.get("sessionId").asText()), UUID.fromString(a.get("commandId").asText()), UUID.fromString(a.get("turnId").asText()), a.get("turns").asLong(), a.get("expectedClockVersion").asLong(), OptionalInt.empty());
                return GmToolOutcome.completed(result.value(), result.version(), result.reference());
            } catch (Exception e) { throw new ToolArgumentInvalidException("invalid advance_game_time arguments"); }
        };
    }
}
