package com.dndmaster.relay.infrastructure;

import static org.junit.jupiter.api.Assertions.*;
import com.dndmaster.relay.application.ConnectionLocationLease;
import java.time.*;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.*;

@Testcontainers
class RedisConnectionLocationRepositoryIntegrationTest {
    @Container static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7.4-alpine").withExposedPorts(6379);
    private LettuceConnectionFactory connectionFactory;

    @AfterEach void close() { if (connectionFactory != null) connectionFactory.destroy(); }

    @Test void leaseHasTtlAndReleaseRequiresCurrentConnectionId() {
        connectionFactory = new LettuceConnectionFactory(REDIS.getHost(), REDIS.getMappedPort(6379));
        connectionFactory.afterPropertiesSet();
        var template = new ReactiveStringRedisTemplate(connectionFactory, RedisSerializationContext.string());
        var clock = Clock.systemUTC();
        var repository = new RedisConnectionLocationRepository(template, clock);
        var player = UUID.randomUUID();
        var old = new ConnectionLocationLease(player, "a", "http://relay-a:8080", "s1", "old", clock.instant());
        repository.claim(old, Duration.ofSeconds(30)).block();
        repository.claim(new ConnectionLocationLease(player, "c", "http://relay-c:8080", "s2", "new", clock.instant()), Duration.ofSeconds(60)).block();

        assertFalse(repository.release(player, "old").block());
        assertFalse(repository.renew(old, Duration.ofSeconds(90)).block());
        assertEquals("new", repository.find(player).block().orElseThrow().connectionId());
        Long ttl = template.getExpire("agent-connection-location:" + player).block().toSeconds();
        assertTrue(ttl > 0 && ttl <= 60);
        assertTrue(repository.release(player, "new").block());
        assertTrue(repository.find(player).block().isEmpty());
    }
}
