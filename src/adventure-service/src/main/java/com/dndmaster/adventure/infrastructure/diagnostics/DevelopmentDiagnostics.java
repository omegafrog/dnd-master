package com.dndmaster.adventure.infrastructure.diagnostics;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.slf4j.Logger;

/** Opt-in diagnostic logging for local development runs. */
public final class DevelopmentDiagnostics {
    public static final String FLAG = "ADVENTURE_RUNTIME_DIAGNOSTICS_ENABLED";

    private DevelopmentDiagnostics() {}

    public static boolean enabled() {
        return Boolean.parseBoolean(System.getProperty(FLAG, System.getenv(FLAG)));
    }

    /** Starts a safe, opt-in duration record. Context must contain identifiers and fixed labels only. */
    public static long begin(Logger logger, String operation, String context) {
        if (!enabled()) return 0L;
        logger.info("dev_flow operation={} outcome=started context={}", operation, context);
        return System.nanoTime();
    }

    public static void complete(Logger logger, String operation, String context, long startedAtNanos) {
        finish(logger, operation, context, startedAtNanos, "completed", "");
    }

    public static void fail(Logger logger, String operation, String context, long startedAtNanos, Throwable failure) {
        finish(logger, operation, context, startedAtNanos, "failed",
                " failureClass=" + failure.getClass().getSimpleName());
    }

    public static <T> T measure(Logger logger, String operation, String context, Supplier<T> action) {
        long startedAt = begin(logger, operation, context);
        try {
            T result = action.get();
            complete(logger, operation, context, startedAt);
            return result;
        } catch (RuntimeException | Error failure) {
            fail(logger, operation, context, startedAt, failure);
            throw failure;
        }
    }

    public static void measure(Logger logger, String operation, String context, Runnable action) {
        long startedAt = begin(logger, operation, context);
        try {
            action.run();
            complete(logger, operation, context, startedAt);
        } catch (RuntimeException | Error failure) {
            fail(logger, operation, context, startedAt, failure);
            throw failure;
        }
    }

    private static void finish(Logger logger, String operation, String context, long startedAtNanos,
            String outcome, String extra) {
        if (startedAtNanos == 0L) return;
        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAtNanos);
        logger.info("dev_flow operation={} outcome={} elapsedMs={} context={}{}",
                operation, outcome, elapsedMs, context, extra);
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
