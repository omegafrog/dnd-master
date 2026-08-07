package com.dndmaster.adventure.domain.runtime;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;

/** Immutable lifecycle aggregate. Terminal states cannot be reopened. */
public final class GmTurn {
    private final UUID turnId;
    private final UUID commandId;
    private final long expectedSessionVersion;
    private final GmInput input;
    private final String fingerprint;
    private final GmTurnStatus status;
    private final String failure;
    private final String providerMetadata;

    private GmTurn(UUID turnId, UUID commandId, long expectedSessionVersion, GmInput input,
                   String fingerprint, GmTurnStatus status, String failure, String providerMetadata) {
        this.turnId = Objects.requireNonNull(turnId);
        this.commandId = Objects.requireNonNull(commandId);
        if (expectedSessionVersion < 0) throw new IllegalArgumentException("expected session version must not be negative");
        this.expectedSessionVersion = expectedSessionVersion;
        this.input = Objects.requireNonNull(input);
        this.fingerprint = Objects.requireNonNull(fingerprint);
        this.status = Objects.requireNonNull(status);
        this.failure = failure;
        this.providerMetadata = providerMetadata;
    }

    public static GmTurn start(UUID turnId, UUID commandId, long expectedSessionVersion, GmInput input) {
        return new GmTurn(turnId, commandId, expectedSessionVersion, input, fingerprint(input), GmTurnStatus.STARTED, null, null);
    }

    public GmTurn process() { return transition(GmTurnStatus.PROCESSING, null, providerMetadata); }
    public GmTurn commit(String providerMetadata) { return transition(GmTurnStatus.COMMITTED, null, providerMetadata); }
    public GmTurn fail(String failure) { return transition(GmTurnStatus.FAILED, required(failure, "failure"), providerMetadata); }
    public GmTurn retry() { return transition(GmTurnStatus.PROCESSING, null, providerMetadata); }

    public void assertSameCommand(GmInput other) {
        if (!fingerprint.equals(fingerprint(Objects.requireNonNull(other)))) {
            throw new IllegalStateException("command id reused with different payload");
        }
    }

    private GmTurn transition(GmTurnStatus target, String nextFailure, String nextProvider) {
        if (status == GmTurnStatus.COMMITTED) {
            throw new IllegalStateException("terminal GM turn cannot transition");
        }
        if (target == GmTurnStatus.PROCESSING && status != GmTurnStatus.STARTED && status != GmTurnStatus.FAILED
                || target == GmTurnStatus.COMMITTED && status != GmTurnStatus.PROCESSING
                || target == GmTurnStatus.FAILED && status != GmTurnStatus.PROCESSING) {
            throw new IllegalStateException("invalid GM turn transition: " + status + " -> " + target);
        }
        return new GmTurn(turnId, commandId, expectedSessionVersion, input, fingerprint, target, nextFailure, nextProvider);
    }

    private static String fingerprint(GmInput input) {
        String canonical = switch (input) {
            case GmInput.TextInput text -> "TEXT|" + text.text();
            case GmInput.MapActionInput map -> "MAP_ACTION|" + map.mapId() + "|" + map.mapVersion() + "|" + map.action();
            case GmInput.MetaQuestionInput question -> "META_QUESTION|" + question.question();
        };
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value.trim();
    }

    public UUID turnId() { return turnId; }
    public UUID commandId() { return commandId; }
    public long expectedSessionVersion() { return expectedSessionVersion; }
    public GmInput input() { return input; }
    public String fingerprint() { return fingerprint; }
    public GmTurnStatus status() { return status; }
    public String failure() { return failure; }
    public String providerMetadata() { return providerMetadata; }
}
