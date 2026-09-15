package com.dndmaster.relay.infrastructure;

import static org.junit.jupiter.api.Assertions.*;
import java.time.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import com.dndmaster.relay.application.ConnectionLocationLease;

class InMemoryConnectionLocationRepositoryContractTest {
    @Test void refreshesTtlAndOldConnectionCannotDeleteReplacement() {
        var clock = Clock.fixed(Instant.parse("2026-09-15T00:00:00Z"), ZoneOffset.UTC);
        var repository = new InMemoryConnectionLocationRepository(clock);
        var player = UUID.randomUUID();
        repository.renew(new ConnectionLocationLease(player, "a", "http://a", "s1", "old", clock.instant()), Duration.ofSeconds(30)).block();
        repository.renew(new ConnectionLocationLease(player, "c", "http://c", "s2", "new", clock.instant()), Duration.ofSeconds(60)).block();

        assertFalse(repository.release(player, "old").block());
        var current = repository.find(player).block().orElseThrow();
        assertEquals("new", current.connectionId());
        assertEquals(clock.instant().plusSeconds(60), current.expiresAt());
        assertTrue(repository.release(player, "new").block());
        assertTrue(repository.find(player).block().isEmpty());
    }
}
