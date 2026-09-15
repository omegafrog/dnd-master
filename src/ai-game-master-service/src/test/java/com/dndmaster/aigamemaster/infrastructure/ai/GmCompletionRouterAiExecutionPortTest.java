package com.dndmaster.aigamemaster.infrastructure.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.dndmaster.aigamemaster.application.ai.AiExecutionRequest;
import com.dndmaster.aigamemaster.application.ai.AiExecutionSuccess;
import com.dndmaster.aigamemaster.configuration.GmProviderProperties;
import com.dndmaster.aigamemaster.infrastructure.ai.SafeAiAuditLogger;
import java.net.URI;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;

class GmCompletionRouterAiExecutionPortTest {
    @Test
    void sends_completed_prompt_and_server_confirmed_identity_to_the_common_execution_port_before_parsing() {
        AtomicReference<AiExecutionRequest> captured = new AtomicReference<>();
        var router = new GmCompletionRouter(new SpringAiChatAdapter(mock(ChatModel.class), 1,
                new SafeAiAuditLogger(message -> { })),
                new GmProviderProperties("codex-cli", "gpt-5.6-luna", "medium", URI.create("https://api.openai.com/"), "", Duration.ofSeconds(5)),
                null, request -> { captured.set(request); return new AiExecutionSuccess("{\"answer\":\"kept\"}"); });
        UUID soloPlayerId = UUID.fromString("00000000-0000-0000-0000-000000000322");

        String parsed = router.complete(soloPlayerId, "request-322", new GmPrompt("completed prompt"), value -> "parsed:" + value);

        assertThat(parsed).isEqualTo("parsed:{\"answer\":\"kept\"}");
        assertThat(captured.get().soloPlayerId()).isEqualTo(soloPlayerId);
        assertThat(captured.get().requestId()).isEqualTo("request-322");
        assertThat(captured.get().completedPrompt()).isEqualTo("completed prompt");
        assertThat(captured.get().model()).isEqualTo("gpt-5.6-luna");
    }
}
