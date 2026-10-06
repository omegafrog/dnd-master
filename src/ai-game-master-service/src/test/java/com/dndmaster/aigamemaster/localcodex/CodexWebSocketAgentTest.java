package com.dndmaster.aigamemaster.localcodex;

import static org.assertj.core.api.Assertions.assertThat;

import com.dndmaster.aigamemaster.application.ai.AiExecutionFailure;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CodexWebSocketAgentTest {
    @Test
    void returnsAuthenticationRejectionAsAReauthenticationRequiredResult() {
        try (var agent = new CodexWebSocketAgent(URI.create("ws://localhost/agent"), "local-token",
                ignored -> new AiExecutionFailure(AiExecutionFailure.Reason.REAUTH_REQUIRED, "safe failure"),
                new ObjectMapper())) {
            var request = new CodexWebSocketAgent.AgentExecutionRequest(UUID.randomUUID(), "request-reauth",
                    "operation-reauth", "completed conversation prompt", "model", "medium", "TEXT",
                    null, List.of(), System.currentTimeMillis() + 60_000, "", null);

            var response = agent.execute(request);

            assertThat(response.failureType()).isEqualTo("REAUTH_REQUIRED");
            assertThat(response.content()).isEmpty();
        }
    }
}
