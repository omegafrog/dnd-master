package com.dndmaster.adventure.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import com.dndmaster.adventure.application.runtime.RuntimeGmInputLimitException;
import org.junit.jupiter.api.Test;

class ScenarioExceptionHandlerTest {
    @Test
    void required_input_limit_is_a_retryable_service_unavailable_response() {
        var response = new ScenarioExceptionHandler().runtimeGmInputLimit(
                new RuntimeGmInputLimitException("required material exceeds input limit"));
        assertEquals(503, response.getStatusCode().value());
        assertEquals("GM_TURN_FAILED_RETRYABLE", response.getBody().get("error"));
        assertEquals(true, response.getBody().get("retryable"));
    }
}
