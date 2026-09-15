package com.dndmaster.aigamemaster.infrastructure.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.dndmaster.aigamemaster.application.ai.AiExecutionFailure;
import com.dndmaster.aigamemaster.application.ai.AiExecutionRequest;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RemoteAiExecutionPortTest {
    @Test
    void reports_a_typed_connection_unavailable_failure_without_a_local_fallback() {
        var result = new RemoteAiExecutionPort().execute(new AiExecutionRequest(
                UUID.fromString("00000000-0000-0000-0000-000000000322"), "request-322", "work-322", "prompt",
                "gpt-5.6-luna", "medium", "TEXT", null, ""));

        assertThat(result).isInstanceOf(AiExecutionFailure.class);
        assertThat(((AiExecutionFailure) result).reason()).isEqualTo(AiExecutionFailure.Reason.CONNECTION_UNAVAILABLE);
    }
}
