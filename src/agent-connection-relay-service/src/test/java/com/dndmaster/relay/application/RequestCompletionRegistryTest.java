package com.dndmaster.relay.application;

import static org.junit.jupiter.api.Assertions.*;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

class RequestCompletionRegistryTest {
    @Test void keepsReportedUsageWithTheCompletedRequest() {
        var registry = new RequestCompletionRegistry();
        var pending = registry.open("request-usage", Duration.ofSeconds(1));
        assertTrue(registry.complete(RelayExecutionResult.success("request-usage", "final",
                new RelayExecutionUsage(120L, 0L, null))));
        StepVerifier.create(pending.result()).assertNext(result -> {
            assertEquals("final", result.content());
            assertEquals(120L, result.usage().inputTokens());
            assertEquals(0L, result.usage().cachedInputTokens());
            assertNull(result.usage().outputTokens());
        }).verifyComplete();
    }
    @Test void requestIdCompletesExactlyOnce() {
        var registry = new RequestCompletionRegistry();
        var pending = registry.await("request-1", Duration.ofSeconds(1));
        assertTrue(registry.complete("request-1", "first"));
        assertFalse(registry.complete("request-1", "second"));
        StepVerifier.create(pending).expectNext("first").verifyComplete();
    }

    @Test void rejectsDuplicateBeforeASecondCompletionSinkIsCreated() {
        var registry = new RequestCompletionRegistry();
        assertTrue(registry.open("request-duplicate", Duration.ofSeconds(1)).accepted());
        var duplicate = registry.open("request-duplicate", Duration.ofSeconds(1));

        assertFalse(duplicate.accepted());
        assertTrue(registry.complete("request-duplicate", "first"));
        assertFalse(registry.complete("request-duplicate", "second"));
    }
}
