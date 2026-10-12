package com.dndmaster.relay.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

import com.dndmaster.relay.application.ConnectionLeaseService;
import com.dndmaster.relay.application.IdentityServicePort;
import com.dndmaster.relay.application.LocalConnectionRegistry;
import com.dndmaster.relay.application.RelayFailureType;
import com.dndmaster.relay.application.RequestCompletionRegistry;
import com.dndmaster.relay.infrastructure.HttpRagToolSearch;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AgentWebSocketHandlerTest {
    @Test
    void preservesOutputLimitFailureFromAgentResponse() {
        var mapper = new ObjectMapper();
        var completions = new RequestCompletionRegistry();
        var handler = new AgentWebSocketHandler(mock(IdentityServicePort.class), mock(LocalConnectionRegistry.class),
                mock(RelayInstanceProperties.class), mapper, completions, mock(ConnectionLeaseService.class),
                mock(HttpRagToolSearch.class));
        var pending = completions.open("output-limit", Duration.ofSeconds(1));

        handler.receiveMessage("{\"requestId\":\"output-limit\",\"content\":\"\",\"failureType\":\"OUTPUT_LIMIT\"}",
                UUID.randomUUID(), "connection", null).block();

        assertEquals(RelayFailureType.OUTPUT_LIMIT, pending.result().block().failureType());
        completions.close("output-limit");
    }
}
