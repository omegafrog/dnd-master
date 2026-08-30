package com.dndmaster.adventure;

import com.dndmaster.adventure.application.runtime.ApprovedPromptConfiguration;
import com.dndmaster.adventure.application.runtime.EnvironmentApprovedPromptConfigurationReadPort;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EnvironmentApprovedPromptConfigurationReadPortTest {
    @Test
    void exposes_only_configured_approved_role_projections() {
        var port = new EnvironmentApprovedPromptConfigurationReadPort(Map.of(
                "WRITER", new ApprovedPromptConfiguration("WRITER", "p-1", "m-1", "run-1", null, "d-1", "e-1", 1)));

        assertEquals("p-1", port.current("writer").orElseThrow().promptVersion());
        assertEquals(true, port.current("PLANNER").isEmpty());
    }
}
