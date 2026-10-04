package com.dndmaster.relay.api;

import com.dndmaster.relay.application.AgentConnectionControlResult;
import com.dndmaster.relay.application.ConnectionControlCommand;
import com.dndmaster.relay.application.ConnectionControlRequest;
import com.dndmaster.relay.application.IdentityServicePort;
import com.dndmaster.relay.application.LocalConnectionExecutor;
import com.dndmaster.relay.application.RelayConnectionControlService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

@RestController
public final class InternalConnectionControlController {
    private final RelayConnectionControlService dispatcher;
    private final LocalConnectionExecutor local;
    private final IdentityServicePort identity;
    private final byte[] internalToken;
    private final byte[] relayPeerToken;

    public InternalConnectionControlController(RelayConnectionControlService dispatcher,
            LocalConnectionExecutor local, IdentityServicePort identity,
            @Value("${relay.internal-token:${INTERNAL_SERVICE_TOKEN:}}") String internalToken,
            @Value("${relay.peer-token:${RELAY_INTERNAL_SERVICE_TOKEN:${INTERNAL_SERVICE_TOKEN:}}}") String relayPeerToken) {
        this.dispatcher = dispatcher;
        this.local = local;
        this.identity = identity;
        this.internalToken = bytes(internalToken, "INTERNAL_SERVICE_TOKEN");
        this.relayPeerToken = bytes(relayPeerToken, "RELAY_INTERNAL_SERVICE_TOKEN");
    }

    @PostMapping("/internal/connection-controls")
    Mono<AgentConnectionControlResult> control(
            @RequestHeader(value = "X-Internal-Token", required = false) String serviceToken,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody ConnectionControlCommand command) {
        authenticate(internalToken, serviceToken);
        UUID playerId = identity.introspectUser(bearerToken(authorization));
        return dispatcher.execute(new ConnectionControlRequest(playerId, command.requestId(), command.action(),
                command.operationId(), command.operationType(), 0, ""));
    }

    @PostMapping("/internal/owned-connection-controls")
    Mono<AgentConnectionControlResult> controlOwned(
            @RequestHeader(value = "X-Internal-Token", required = false) String serviceToken,
            @RequestHeader(value = "X-Internal-Caller", required = false) String internalCaller,
            @RequestHeader(value = "X-Relay-Instance-Id", required = false) String relayInstanceId,
            @RequestBody ConnectionControlRequest request) {
        authenticate(relayPeerToken, serviceToken);
        if (!"agent-connection-relay-service".equals(internalCaller)
                || relayInstanceId == null || relayInstanceId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "relay caller identity is required");
        }
        return local.control(request);
    }

    private static void authenticate(byte[] expected, String supplied) {
        byte[] actual = supplied == null ? new byte[0] : supplied.getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(expected, actual)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid internal token");
        }
    }

    private static String bearerToken(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")
                || authorization.substring("Bearer ".length()).isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "authenticated player is required");
        }
        return authorization.substring("Bearer ".length()).trim();
    }

    private static byte[] bytes(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalStateException(name + " is required");
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
