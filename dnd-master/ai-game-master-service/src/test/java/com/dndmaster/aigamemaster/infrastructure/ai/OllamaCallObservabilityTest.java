package com.dndmaster.aigamemaster.infrastructure.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class OllamaCallObservabilityTest {
    @Test
    void opensCircuitAfterConfiguredFailuresAndNeverRetainsPayload() {
        OllamaCallObservability observability = new OllamaCallObservability(2, Duration.ofSeconds(10));
        String secretPrompt = "PRIVATE_PROMPT_DO_NOT_LOG";

        assertThrows(IllegalStateException.class, () -> observability.invoke(() -> { throw new IllegalStateException(secretPrompt); }));
        assertThrows(IllegalStateException.class, () -> observability.invoke(() -> { throw new IllegalStateException(secretPrompt); }));
        assertEquals(OllamaCallObservability.CircuitState.OPEN, observability.state());
        assertThrows(IllegalStateException.class, () -> observability.invoke(() -> "must-not-run"));
    }
}
