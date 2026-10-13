package com.dndmaster.aigamemaster.localcodex;

import static org.junit.jupiter.api.Assertions.*;

import com.dndmaster.aigamemaster.application.ai.AiExecutionSuccess;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith(OutputCaptureExtension.class)
class CodexWebSocketAgentDiagnosticsTest {
    @Test
    void logsCodexCompletionMetadataWithoutPromptOrResponse(CapturedOutput output) {
        var agent = new CodexWebSocketAgent(URI.create("ws://localhost:1"), "private-token",
                request -> new AiExecutionSuccess("private response"), new ObjectMapper());
        var request = new CodexWebSocketAgent.AgentExecutionRequest(UUID.randomUUID(), "req-123", "op-456",
                "private prompt", "model", "medium", "JSON", null, List.of(), 0, "", null, List.of());
        String previous = System.getProperty("ADVENTURE_RUNTIME_DIAGNOSTICS_ENABLED");
        System.setProperty("ADVENTURE_RUNTIME_DIAGNOSTICS_ENABLED", "true");
        try {
            var response = agent.execute(request);
            assertEquals("private response", response.content());
            assertTrue(output.getAll().contains("dev_user_pc_ai_execution requestId=req-123 operationId=op-456 stage=codex_execution_complete outcome=success contentLength=16"));
            assertFalse(output.getAll().contains("private prompt"));
            assertFalse(output.getAll().contains("private response"));
            assertFalse(output.getAll().contains("private-token"));
        } finally {
            if (previous == null) System.clearProperty("ADVENTURE_RUNTIME_DIAGNOSTICS_ENABLED");
            else System.setProperty("ADVENTURE_RUNTIME_DIAGNOSTICS_ENABLED", previous);
            agent.close();
        }
    }
}
