package com.dndmaster.relay.application;

import java.net.URI;
import java.time.Instant;
import java.util.UUID;

public record ConnectionLocationLease(UUID soloPlayerId, String instanceId, String internalAddress,
                                      String sessionId, String connectionId, Instant expiresAt) {
    public ConnectionLocationLease {
        if (soloPlayerId == null) throw new IllegalArgumentException("soloPlayerId is required");
        instanceId = required(instanceId, "instanceId");
        internalAddress = required(internalAddress, "internalAddress");
        var uri = URI.create(internalAddress);
        if (uri.getScheme() == null || uri.getHost() == null) throw new IllegalArgumentException("internalAddress must be absolute");
        sessionId = required(sessionId, "sessionId");
        connectionId = required(connectionId, "connectionId");
        if (expiresAt == null) throw new IllegalArgumentException("expiresAt is required");
    }
    private static String required(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
        return value.trim();
    }
}
