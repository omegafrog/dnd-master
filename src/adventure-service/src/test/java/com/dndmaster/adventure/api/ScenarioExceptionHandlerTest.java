package com.dndmaster.adventure.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import com.dndmaster.adventure.application.runtime.RuntimeGmInputLimitException;
import org.junit.jupiter.api.Test;

class ScenarioExceptionHandlerTest {
    @Test
    void state_conflict_does_not_recommend_unrelated_blueprint_republication() {
        var response = new ScenarioExceptionHandler().stateConflict(new IllegalStateException("runtime generation failed"));

        assertEquals(409, response.getStatusCode().value());
        assertEquals("ADVENTURE_START_BLOCKED", response.getBody().get("error"));
        assertEquals("runtime generation failed", response.getBody().get("message"));
        assertFalse(response.getBody().containsKey("recovery"));
    }

    @Test
    void required_input_limit_is_a_retryable_service_unavailable_response() {
        var response = new ScenarioExceptionHandler().runtimeGmInputLimit(
                new RuntimeGmInputLimitException("required material exceeds input limit"));
        assertEquals(503, response.getStatusCode().value());
        assertEquals("GM_TURN_FAILED_RETRYABLE", response.getBody().get("error"));
        assertEquals(true, response.getBody().get("retryable"));
    }
}
