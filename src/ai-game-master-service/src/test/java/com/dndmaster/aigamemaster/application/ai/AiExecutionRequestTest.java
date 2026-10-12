package com.dndmaster.aigamemaster.application.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AiExecutionRequestTest {
    private static final UUID SOLO_PLAYER_ID = UUID.fromString("00000000-0000-0000-0000-000000000322");

    @Test
    void retains_the_server_confirmed_identity_and_completed_execution_input() throws Exception {
        var schema = new ObjectMapper().readTree("{\"type\":\"array\"}");

        var request = new AiExecutionRequest(SOLO_PLAYER_ID, "request-322", "work-322", "completed prompt",
                "gpt-5.6-luna", "medium", "JSON_ARRAY", schema, "data:image/png;base64,AA==");

        assertThat(request.soloPlayerId()).isEqualTo(SOLO_PLAYER_ID);
        assertThat(request.requestId()).isEqualTo("request-322");
        assertThat(request.completedPrompt()).isEqualTo("completed prompt");
        assertThat(request.outputSchema()).isEqualTo(schema);
        assertThat(request.imageDataUri()).startsWith("data:image/");
    }

    @Test
    void rejects_missing_server_confirmed_identity_or_request_input() {
        assertThatIllegalArgumentException().isThrownBy(() -> new AiExecutionRequest(null, "request", "work", "prompt",
                "model", "medium", "TEXT", null, ""));
        assertThatIllegalArgumentException().isThrownBy(() -> new AiExecutionRequest(SOLO_PLAYER_ID, "", "work", "prompt",
                "model", "medium", "TEXT", null, ""));
        assertThatIllegalArgumentException().isThrownBy(() -> new AiExecutionRequest(SOLO_PLAYER_ID, "request", "work", "prompt",
                "model", "medium", "TEXT", null, "https://example.test/image.png"));
    }

    @Test
    void preserves_fixed_child_stageIds_prompts_and_schema_and_rejects_duplicate_ids() throws Exception {
        var mapper = new ObjectMapper();
        var schema = mapper.readTree("{\"type\":\"object\",\"properties\":{},\"required\":[],\"additionalProperties\":false}");
        var tasks = java.util.List.of(new AiExecutionRequest.Subtask("stage-a", "task A", schema),
                new AiExecutionRequest.Subtask("stage-b", "task B", schema));
        var request = new AiExecutionRequest(SOLO_PLAYER_ID, "request", "work", "parent", "model", "medium",
                "JSON", schema, "", null, tasks);

        assertThat(request.subtasks()).extracting(AiExecutionRequest.Subtask::stageId).containsExactly("stage-a", "stage-b");
        assertThat(request.subtasks().getFirst().outputSchema()).isEqualTo(schema);
        assertThatIllegalArgumentException().isThrownBy(() -> new AiExecutionRequest(SOLO_PLAYER_ID, "request", "work",
                "parent", "model", "medium", "JSON", schema, "", null, java.util.List.of(tasks.getFirst(), tasks.getFirst())));
    }
}
