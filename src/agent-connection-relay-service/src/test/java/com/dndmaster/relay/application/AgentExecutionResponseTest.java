package com.dndmaster.relay.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class AgentExecutionResponseTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void readsSuccessfulResponseFromAgent() throws Exception {
        var response = objectMapper.readValue(
                "{\"requestId\":\"req-123\",\"content\":\"완료\",\"failureType\":null}",
                AgentExecutionResponse.class);

        assertEquals("req-123", response.requestId());
        assertEquals("완료", response.content());
        assertTrue(response.success());
        assertEquals(new RelayExecutionResult("req-123", "완료", null), response.toRelayExecutionResult());
    }

    @Test
    void readsFailedResponseFromAgent() throws Exception {
        var response = objectMapper.readValue(
                "{\"requestId\":\"req-123\",\"content\":null,\"failureType\":\"REMOTE_FAILURE\"}",
                AgentExecutionResponse.class);

        assertEquals("", response.content());
        assertFalse(response.success());
        assertEquals(RelayFailureType.REMOTE_FAILURE, response.failureType());
        assertEquals(new RelayExecutionResult("req-123", "", RelayFailureType.REMOTE_FAILURE),
                response.toRelayExecutionResult());
    }

    @Test
    void rejectsResponseWithoutRequestId() {
        assertThrows(IllegalArgumentException.class,
                () -> new AgentExecutionResponse("", "content", null));
    }
}
