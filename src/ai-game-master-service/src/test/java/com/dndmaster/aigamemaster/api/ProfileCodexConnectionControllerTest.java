package com.dndmaster.aigamemaster.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dndmaster.aigamemaster.application.endpoint.ConnectionContext;
import com.dndmaster.aigamemaster.application.endpoint.ConnectionOperationState;
import com.dndmaster.aigamemaster.application.endpoint.ConnectionOperationType;
import com.dndmaster.aigamemaster.application.endpoint.ProviderConnectionApplicationService;
import com.dndmaster.aigamemaster.application.endpoint.ProviderConnectionState;
import com.dndmaster.aigamemaster.application.endpoint.RemoteProviderConnectionPort;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ProfileCodexConnectionControllerTest {
    @Test
    void exposesStatusStartPollAndInstallationDisconnectThroughTheProfileConnectionResource() {
        var port = new FakePort();
        var controller = new ProfileCodexConnectionController(new ProviderConnectionApplicationService(port));
        String authorization = "Bearer user-session";

        assertThat(controller.status(authorization).status()).isEqualTo("CONNECTED");
        var started = controller.start(authorization, new ProfileCodexConnectionController.StartRequest("SWITCH_ACCOUNT"));
        assertThat(started.status()).isEqualTo("AUTHENTICATING");
        assertThat(started.pending()).isTrue();
        assertThat(started.authUrl()).isEqualTo("https://auth.example/approve");
        assertThat(controller.operation(authorization, UUID.fromString(started.operationId())).status())
                .isEqualTo("AUTHENTICATING");
        assertThat(controller.disconnect(authorization).status()).isEqualTo("DISCONNECTED");

        assertThat(port.lastContext.authorization()).isEqualTo(authorization);
        assertThat(port.lastType).isEqualTo(ConnectionOperationType.SWITCH_ACCOUNT);
        assertThat(port.lastOperationId).isEqualTo(started.operationId());
        assertThat(port.disconnectCalls).isEqualTo(1);
    }

    @Test
    void rejectsMissingProfileAuthenticationAndUnknownOperationTypes() {
        var controller = new ProfileCodexConnectionController(new ProviderConnectionApplicationService(new FakePort()));
        assertThatThrownBy(() -> controller.status(null))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        assertThatThrownBy(() -> controller.start("Bearer user-session",
                new ProfileCodexConnectionController.StartRequest("LOGOUT")))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    }

    private static final class FakePort implements RemoteProviderConnectionPort {
        private ConnectionContext lastContext;
        private ConnectionOperationType lastType;
        private String lastOperationId;
        private int disconnectCalls;

        @Override public ProviderConnectionState getStatus(ConnectionContext context, UUID requestId) {
            lastContext = context;
            return new ProviderConnectionState("CONNECTED", true, null, null);
        }
        @Override public ConnectionOperationState startOperation(ConnectionContext context, UUID requestId,
                UUID operationId, ConnectionOperationType type) {
            lastContext = context;
            lastType = type;
            lastOperationId = operationId.toString();
            return new ConnectionOperationState(operationId.toString(), "AUTHENTICATING",
                    "https://auth.example/approve", "브라우저에서 승인해 주세요.", true);
        }
        @Override public ConnectionOperationState getOperation(ConnectionContext context, UUID requestId, UUID operationId) {
            lastContext = context;
            lastOperationId = operationId.toString();
            return new ConnectionOperationState(operationId.toString(), "AUTHENTICATING", null, null, true);
        }
        @Override public ProviderConnectionState disconnect(ConnectionContext context, UUID requestId) {
            lastContext = context;
            disconnectCalls++;
            return new ProviderConnectionState("DISCONNECTED", true, null, null);
        }
    }
}
