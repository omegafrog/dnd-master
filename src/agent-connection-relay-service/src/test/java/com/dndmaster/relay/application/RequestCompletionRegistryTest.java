package com.dndmaster.relay.application;

import static org.junit.jupiter.api.Assertions.*;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

class RequestCompletionRegistryTest {
    @Test void requestIdCompletesExactlyOnce() {
        var registry = new RequestCompletionRegistry();
        var pending = registry.await("request-1", Duration.ofSeconds(1));
        assertTrue(registry.complete("request-1", "first"));
        assertFalse(registry.complete("request-1", "second"));
        StepVerifier.create(pending).expectNext("first").verifyComplete();
    }
}
