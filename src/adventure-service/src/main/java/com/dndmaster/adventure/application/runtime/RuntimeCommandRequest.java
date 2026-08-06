package com.dndmaster.adventure.application.runtime;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;

public record RuntimeCommandRequest(UUID commandId, UUID sessionId, UUID turnId, UUID ownerPlayerId,
                                    String toolName, String argumentsJson) {
    public RuntimeCommandRequest {
        Objects.requireNonNull(commandId); Objects.requireNonNull(sessionId); Objects.requireNonNull(turnId);
        Objects.requireNonNull(ownerPlayerId); if (toolName == null || toolName.isBlank()) throw new IllegalArgumentException("tool name required");
        if (argumentsJson == null) throw new NullPointerException("arguments json");
    }
    public String fingerprint() {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest((sessionId + "|" + turnId + "|" + ownerPlayerId + "|" + toolName + "|" + argumentsJson).getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}
