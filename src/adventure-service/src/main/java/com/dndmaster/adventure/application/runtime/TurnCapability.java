package com.dndmaster.adventure.application.runtime;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Opaque, single-turn authorization presented only to the tool gateway. */
public record TurnCapability(String token, UUID sessionId, UUID turnId, UUID ownerPlayerId,
                             Set<String> allowedTools, Instant expiresAt, UUID nonce) {
    public TurnCapability {
        token = required(token, "token");
        Objects.requireNonNull(sessionId, "session id must not be null");
        Objects.requireNonNull(turnId, "turn id must not be null");
        Objects.requireNonNull(ownerPlayerId, "owner player id must not be null");
        allowedTools = Set.copyOf(Objects.requireNonNull(allowedTools, "allowed tools must not be null"));
        if (allowedTools.stream().anyMatch(name -> name == null || name.isBlank())) throw new IllegalArgumentException("tool name must not be blank");
        Objects.requireNonNull(expiresAt, "expiry must not be null");
        Objects.requireNonNull(nonce, "nonce must not be null");
    }

    public static TurnCapability issue(UUID sessionId, UUID turnId, UUID ownerPlayerId, Set<String> allowedTools,
                                       Instant expiresAt, UUID nonce) {
        return new TurnCapability(UUID.randomUUID().toString() + "." + UUID.randomUUID(), sessionId, turnId,
                ownerPlayerId, allowedTools, expiresAt, nonce);
    }

    public TurnCapability withExpiry(Instant expiry) {
        return new TurnCapability(token, sessionId, turnId, ownerPlayerId, allowedTools, expiry, nonce);
    }

    void authorize(GmToolInvocation invocation, Instant now) {
        if (!allowedTools.contains(invocation.toolName())) throw denied(ToolCapabilityDenialReason.TOOL_NOT_ALLOWED, invocation);
        if (!sessionId.equals(invocation.sessionId())) throw denied(ToolCapabilityDenialReason.SESSION_MISMATCH, invocation);
        if (!turnId.equals(invocation.turnId())) throw denied(ToolCapabilityDenialReason.TURN_MISMATCH, invocation);
        if (!ownerPlayerId.equals(invocation.ownerPlayerId())) throw denied(ToolCapabilityDenialReason.OWNER_MISMATCH, invocation);
        if (!expiresAt.isAfter(now)) throw denied(ToolCapabilityDenialReason.EXPIRED, invocation);
    }

    private static ToolAuthorizationException denied(ToolCapabilityDenialReason reason, GmToolInvocation invocation) {
        return new ToolAuthorizationException(reason, invocation.toolName());
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value;
    }
}
