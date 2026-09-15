package com.dndmaster.relay.application;

import static org.junit.jupiter.api.Assertions.*;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.*;
import reactor.test.StepVerifier;

@ExtendWith(OutputCaptureExtension.class)
class RelaySensitiveLoggingTest {
    @Test void logsDoNotContainPromptResultOrLoginMaterial(CapturedOutput output) {
        var request = new RelayExecutionRequest(UUID.randomUUID(), "r", "w", "private-prompt", "m", "medium", "text", null,
                List.of("codex-login-material"));
        var dispatcher = new RelayExecutionDispatcher("a", id -> reactor.core.publisher.Mono.just(Optional.of(
                new ConnectionLocationLease(request.soloPlayerId(), "a", "http://a", "s", "c", java.time.Instant.now().plusSeconds(30)))),
                ignored -> reactor.core.publisher.Mono.just(RelayExecutionResult.success("r", "private-result")),
                (address, ignored) -> reactor.core.publisher.Mono.never(), RelayMetrics.noop(), Duration.ofSeconds(1));
        StepVerifier.create(dispatcher.execute(request)).expectNextCount(1).verifyComplete();
        assertFalse(output.getAll().contains("private-prompt"));
        assertFalse(output.getAll().contains("private-result"));
        assertFalse(output.getAll().contains("codex-login-material"));
    }
}
