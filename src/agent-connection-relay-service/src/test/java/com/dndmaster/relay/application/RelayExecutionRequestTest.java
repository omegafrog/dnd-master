package com.dndmaster.relay.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RelayExecutionRequestTest {
    @Test
    void keeps_structured_stage_tasks_when_the_relay_sets_deadline_and_connection() throws Exception {
        var mapper = new ObjectMapper();
        var schema = mapper.readTree("{\"type\":\"object\",\"properties\":{},\"required\":[],\"additionalProperties\":false}");
        var request = new RelayExecutionRequest(UUID.randomUUID(), "r", "operation", "parent", "model", "ultra",
                "JSON", schema, List.of(), 0, "", null,
                List.of(new RelayExecutionRequest.RelaySubtask("arrival", "child prompt", schema)));

        var routed = request.withDeadline(1234).withConnectionId("local-client");
        var json = mapper.readTree(mapper.writeValueAsString(routed));

        assertThat(json.path("deadlineEpochMillis").asLong()).isEqualTo(1234);
        assertThat(json.path("connectionId").asText()).isEqualTo("local-client");
        assertThat(json.path("subtasks").get(0).path("stageId").asText()).isEqualTo("arrival");
        assertThat(json.path("subtasks").get(0).path("prompt").asText()).isEqualTo("child prompt");
        assertThat(json.path("subtasks").get(0).path("outputSchema")).isEqualTo(schema);
    }
}
