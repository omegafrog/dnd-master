package com.dndmaster.relay.infrastructure;

import com.dndmaster.relay.application.*;
import java.time.*;
import java.util.*;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import reactor.core.publisher.Mono;

public final class RedisConnectionLocationRepository implements ConnectionLocationRepository {
    private static final String PREFIX = "agent-connection-location:";
    private static final DefaultRedisScript<Long> RENEW = new DefaultRedisScript<>(
            "redis.call('HSET', KEYS[1], 'instanceId', ARGV[1], 'internalAddress', ARGV[2], 'sessionId', ARGV[3], 'connectionId', ARGV[4], 'expiresAt', ARGV[5]); " +
                    "redis.call('PEXPIRE', KEYS[1], ARGV[6]); return 1", Long.class);
    private static final DefaultRedisScript<Long> RELEASE = new DefaultRedisScript<>(
            "if redis.call('HGET', KEYS[1], 'connectionId') == ARGV[1] then return redis.call('DEL', KEYS[1]) else return 0 end", Long.class);
    private static final DefaultRedisScript<Long> REFRESH = new DefaultRedisScript<>(
            "if redis.call('HGET', KEYS[1], 'connectionId') == ARGV[4] then " +
                    "redis.call('HSET', KEYS[1], 'instanceId', ARGV[1], 'internalAddress', ARGV[2], 'sessionId', ARGV[3], 'expiresAt', ARGV[5]); " +
                    "redis.call('PEXPIRE', KEYS[1], ARGV[6]); return 1 else return 0 end", Long.class);
    private final ReactiveStringRedisTemplate redis;
    private final Clock clock;
    private final Duration timeout;

    public RedisConnectionLocationRepository(ReactiveStringRedisTemplate redis, Clock clock) {
        this(redis, clock, Duration.ofSeconds(2));
    }
    public RedisConnectionLocationRepository(ReactiveStringRedisTemplate redis, Clock clock, Duration timeout) {
        this.redis = Objects.requireNonNull(redis); this.clock = Objects.requireNonNull(clock); this.timeout = Objects.requireNonNull(timeout);
    }

    @Override public Mono<Optional<ConnectionLocationLease>> find(UUID soloPlayerId) {
        return redis.opsForHash().entries(key(soloPlayerId)).collectMap(entry -> entry.getKey().toString(), entry -> entry.getValue().toString())
                .map(values -> values.isEmpty() ? Optional.<ConnectionLocationLease>empty() : Optional.of(new ConnectionLocationLease(soloPlayerId,
                        required(values, "instanceId"), required(values, "internalAddress"), required(values, "sessionId"),
                        required(values, "connectionId"), Instant.parse(required(values, "expiresAt")))))
                .timeout(timeout);
    }

    @Override public Mono<Void> claim(ConnectionLocationLease lease, Duration ttl) {
        if (ttl.isZero() || ttl.isNegative()) return Mono.error(new IllegalArgumentException("ttl must be positive"));
        var expiresAt = clock.instant().plus(ttl);
        return redis.execute(RENEW, List.of(key(lease.soloPlayerId())), lease.instanceId(), lease.internalAddress(), lease.sessionId(),
                lease.connectionId(), expiresAt.toString(), Long.toString(ttl.toMillis())).single().timeout(timeout).then();
    }

    @Override public Mono<Boolean> renew(ConnectionLocationLease lease, Duration ttl) {
        if (ttl.isZero() || ttl.isNegative()) return Mono.error(new IllegalArgumentException("ttl must be positive"));
        var expiresAt = clock.instant().plus(ttl);
        return redis.execute(REFRESH, List.of(key(lease.soloPlayerId())), lease.instanceId(), lease.internalAddress(), lease.sessionId(),
                lease.connectionId(), expiresAt.toString(), Long.toString(ttl.toMillis())).single().timeout(timeout).map(updated -> updated == 1L);
    }

    @Override public Mono<Boolean> release(UUID soloPlayerId, String connectionId) {
        return redis.execute(RELEASE, List.of(key(soloPlayerId)), connectionId).single().timeout(timeout).map(deleted -> deleted == 1L);
    }

    private static String key(UUID id) { return PREFIX + id; }
    private static String required(Map<String, String> values, String name) {
        var value = values.get(name); if (value == null || value.isBlank()) throw new IllegalStateException("invalid connection location lease"); return value;
    }
}
