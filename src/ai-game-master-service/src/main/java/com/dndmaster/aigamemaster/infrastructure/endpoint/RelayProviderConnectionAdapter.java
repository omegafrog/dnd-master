package com.dndmaster.aigamemaster.infrastructure.endpoint;

import com.dndmaster.aigamemaster.application.endpoint.ConnectionContext;
import com.dndmaster.aigamemaster.application.endpoint.ConnectionOperationState;
import com.dndmaster.aigamemaster.application.endpoint.ConnectionOperationType;
import com.dndmaster.aigamemaster.application.endpoint.ProviderConnectionState;
import com.dndmaster.aigamemaster.application.endpoint.RemoteProviderConnectionPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Calls the existing relay control route and forwards only the authenticated player session. */
@Component
public final class RelayProviderConnectionAdapter implements RemoteProviderConnectionPort {
    private final HttpClient client;
    private final ObjectMapper mapper;
    private final URI endpoint;
    private final String internalToken;
    private final Duration timeout;

    @Autowired
    public RelayProviderConnectionAdapter(ObjectMapper mapper,
            @Value("${ai-game-master.relay.base-url:http://agent-connection-relay-service:8080}") URI relayBaseUri,
            @Value("${ai-game-master.integration.internal-token:${INTERNAL_SERVICE_TOKEN:}}") String internalToken,
            @Value("${ai-game-master.relay.timeout:PT3M}") Duration timeout) {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build(), mapper,
                relayBaseUri.resolve("/internal/connection-controls"), internalToken, timeout);
    }

    RelayProviderConnectionAdapter(HttpClient client, ObjectMapper mapper, URI endpoint,
            String internalToken, Duration timeout) {
        this.client = client;
        this.mapper = mapper;
        this.endpoint = endpoint;
        this.internalToken = internalToken == null ? "" : internalToken;
        this.timeout = timeout;
    }

    @Override public ProviderConnectionState getStatus(ConnectionContext context, UUID requestId) {
        var result = send(context, new ControlRequest(requestId.toString(), "STATUS", "", ""));
        return new ProviderConnectionState(result.status(), result.cliAvailable(), result.operationId(), result.message());
    }

    @Override public ConnectionOperationState startOperation(ConnectionContext context, UUID requestId,
            UUID operationId, ConnectionOperationType type) {
        var result = send(context, new ControlRequest(requestId.toString(), "START", operationId.toString(), type.name()));
        return operation(result);
    }

    @Override public ConnectionOperationState getOperation(ConnectionContext context, UUID requestId, UUID operationId) {
        return operation(send(context, new ControlRequest(requestId.toString(), "POLL", operationId.toString(), "")));
    }

    @Override public ProviderConnectionState disconnect(ConnectionContext context, UUID requestId) {
        var result = send(context, new ControlRequest(requestId.toString(), "DISCONNECT", "", ""));
        return new ProviderConnectionState(result.status(), result.cliAvailable(), result.operationId(), result.message());
    }

    private ControlResult send(ConnectionContext context, ControlRequest command) {
        try {
            HttpRequest request = HttpRequest.newBuilder(endpoint).timeout(timeout)
                    .header("Content-Type", "application/json")
                    .header("X-Internal-Token", internalToken)
                    .header("Authorization", context.authorization())
                    .POST(HttpRequest.BodyPublishers.ofByteArray(mapper.writeValueAsBytes(command))).build();
            var response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() / 100 != 2) return ControlResult.unavailable(command.requestId());
            ControlResult result = mapper.readValue(response.body(), ControlResult.class);
            return command.requestId().equals(result.requestId()) ? result : ControlResult.unavailable(command.requestId());
        } catch (Exception failure) {
            return ControlResult.unavailable(command.requestId());
        }
    }

    private static ConnectionOperationState operation(ControlResult result) {
        return new ConnectionOperationState(result.operationId(), result.status(), result.authUrl(), result.message(), result.pending());
    }

    private record ControlRequest(String requestId, String action, String operationId, String operationType) { }
    private record ControlResult(String messageType, String requestId, String status, boolean cliAvailable,
                                 String operationId, String operationStatus, boolean pending,
                                 String authUrl, String message) {
        private static ControlResult unavailable(String requestId) {
            return new ControlResult("CONNECTION_CONTROL_RESULT", requestId, "UNAVAILABLE", false,
                    null, null, false, null, "사용자 PC 연결을 사용할 수 없습니다. 다시 연결해 주세요.");
        }
    }
}
