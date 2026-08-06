package com.dndmaster.adventure;

import static org.junit.jupiter.api.Assertions.*;

import com.dndmaster.adventure.application.runtime.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GmProviderQualityGateTest {
    @Test
    void passes_only_at_exact_quality_gate_thresholds() {
        GmQualityGateReport report = new GmQualityGateReport(100, 99, 95, 95, 0, 0, 0, 4.0);

        assertTrue(report.passed());
        assertFalse(new GmQualityGateReport(100, 98, 95, 95, 0, 0, 0, 4.0).passed());
        assertFalse(new GmQualityGateReport(100, 99, 94, 95, 0, 0, 0, 4.0).passed());
        assertFalse(new GmQualityGateReport(100, 99, 95, 95, 1, 0, 0, 4.0).passed());
        assertFalse(new GmQualityGateReport(100, 99, 95, 95, 0, 0, 0, 3.99).passed());
    }

    @Test
    void provider_switch_preserves_session_state_and_rejects_mid_turn_mixing() {
        UUID session = UUID.randomUUID();
        ProviderBinding binding = new ProviderBinding(session, new GmProviderSelection("ollama", "qwen3:8b", "medium"), 17);

        ProviderBinding switched = ProviderSwitchPolicy.switchProvider(binding,
                new GmProviderSelection("openai", "gpt-5.6-luna", "medium"), false);

        assertEquals(session, switched.sessionId());
        assertEquals(17, switched.stateVersion());
        assertEquals("openai", switched.selection().provider());
        assertThrows(IllegalStateException.class, () -> ProviderSwitchPolicy.switchProvider(binding,
                new GmProviderSelection("openai", "gpt-5.6-luna", "medium"), true));
    }
}
