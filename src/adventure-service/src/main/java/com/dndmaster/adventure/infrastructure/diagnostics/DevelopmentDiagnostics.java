package com.dndmaster.adventure.infrastructure.diagnostics;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** Opt-in diagnostic logging for local development runs. */
public final class DevelopmentDiagnostics {
    public static final String FLAG = "ADVENTURE_RUNTIME_DIAGNOSTICS_ENABLED";

    private DevelopmentDiagnostics() {}

    public static boolean enabled() {
        return Boolean.parseBoolean(System.getProperty(FLAG, System.getenv(FLAG)));
    }

    public static String fingerprint(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(
                    (value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest, 0, 8);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    public static String safeBody(String value) {
        if (value == null || value.isBlank()) return "";
        String normalized = value.replaceAll("[\\r\\n\\t]+", " ");
        return normalized.substring(0, Math.min(500, normalized.length()));
    }
}
