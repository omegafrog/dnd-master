package com.dndmaster.relay.api;

import com.dndmaster.relay.application.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

@RestController
public final class InternalExecutionController {
    private final ExecutionService dispatcher;
    private final LocalConnectionExecutor local;
    private final byte[] internalToken;

    public InternalExecutionController(ExecutionService dispatcher, String internalToken) {
        this(dispatcher, request -> Mono.just(RelayExecutionResult.failure(request.requestId(), RelayFailureType.NO_CONNECTION)), internalToken);
    }
    @Autowired
    public InternalExecutionController(ExecutionService dispatcher, LocalConnectionExecutor local,
            @Value("${relay.internal-token:${INTERNAL_SERVICE_TOKEN:}}") String internalToken) {
        this.dispatcher = dispatcher; this.local = local;
        if (internalToken == null || internalToken.isBlank()) throw new IllegalStateException("INTERNAL_SERVICE_TOKEN is required");
        this.internalToken = internalToken.getBytes(StandardCharsets.UTF_8);
    }

    @PostMapping("/internal/executions")
    Mono<RelayExecutionResult> execute(@RequestHeader(value = "X-Internal-Token", required = false) String token,
                                       @RequestBody RelayExecutionRequest request) {
        authenticate(token); return dispatcher.execute(request);
    }

    @PostMapping("/internal/owned-executions")
    Mono<RelayExecutionResult> executeOwned(@RequestHeader(value = "X-Internal-Token", required = false) String token,
                                            @RequestBody RelayExecutionRequest request) {
        authenticate(token); return local.execute(request);
    }

    private void authenticate(String token) {
        byte[] supplied = token == null ? new byte[0] : token.getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(internalToken, supplied)) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid internal token");
    }
}
