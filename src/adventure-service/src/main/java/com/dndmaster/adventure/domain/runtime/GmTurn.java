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
    private final RequestedGmProviderSelection requestedSelection;
    private final EffectiveGmProviderSelection effectiveSelection;
    private final int attemptCount;

    private GmTurn(UUID turnId, UUID commandId, long expectedSessionVersion, GmInput input,
                   String fingerprint, GmTurnStatus status, String failure, String providerMetadata,
                   RequestedGmProviderSelection requestedSelection,
                   EffectiveGmProviderSelection effectiveSelection, int attemptCount) {
        this.turnId = Objects.requireNonNull(turnId);
        this.commandId = Objects.requireNonNull(commandId);
        if (expectedSessionVersion < 0) throw new IllegalArgumentException("expected session version must not be negative");
        this.expectedSessionVersion = expectedSessionVersion;
        this.input = Objects.requireNonNull(input);
        this.fingerprint = Objects.requireNonNull(fingerprint);
        this.status = Objects.requireNonNull(status);
        this.failure = failure;
        this.providerMetadata = providerMetadata;
        this.requestedSelection = Objects.requireNonNull(requestedSelection);
        this.effectiveSelection = Objects.requireNonNull(effectiveSelection);
        if (attemptCount < 1) throw new IllegalArgumentException("attempt count must be positive");
        this.attemptCount = attemptCount;
    }

    public static GmTurn start(UUID turnId, UUID commandId, long expectedSessionVersion, GmInput input) {
        return start(turnId, commandId, expectedSessionVersion, input, RequestedGmProviderSelection.legacyUnknown());
    }

    public static GmTurn start(UUID turnId, UUID commandId, long expectedSessionVersion, GmInput input,
                               RequestedGmProviderSelection requestedSelection) {
        return new GmTurn(turnId, commandId, expectedSessionVersion, input, fingerprint(expectedSessionVersion, input), GmTurnStatus.STARTED, null, null,
                requestedSelection, EffectiveGmProviderSelection.legacyUnknown(), 1);
    }

    public GmTurn process() { return transition(GmTurnStatus.PROCESSING, null, providerMetadata); }
    public GmTurn commit(String providerMetadata) { return transition(GmTurnStatus.COMMITTED, null, providerMetadata, requestedSelection, effectiveSelection, attemptCount); }
    public GmTurn commit(String providerMetadata, RequestedGmProviderSelection requested,
                         EffectiveGmProviderSelection effective, int attempts) {
        return transition(GmTurnStatus.COMMITTED, null, providerMetadata, requested, effective, attempts);
    }
    public GmTurn fail(String failure) { return transition(GmTurnStatus.FAILED, required(failure, "failure"), providerMetadata); }
    public GmTurn failRetryable(String failure) {
        return transition(GmTurnStatus.FAILED_RETRYABLE, required(failure, "failure"), providerMetadata);
    }

    public void assertSameCommand(GmInput other) {
        assertSameCommand(expectedSessionVersion, other);
    }

    public void assertSameCommand(long expectedVersion, GmInput other) {
        if (!fingerprint.equals(fingerprint(expectedVersion, Objects.requireNonNull(other)))) {
            throw new IllegalStateException("command id reused with different payload");
        }
    }

    private GmTurn transition(GmTurnStatus target, String nextFailure, String nextProvider) {
        return transition(target, nextFailure, nextProvider, requestedSelection, effectiveSelection, attemptCount);
    }

    private GmTurn transition(GmTurnStatus target, String nextFailure, String nextProvider,
                              RequestedGmProviderSelection requested, EffectiveGmProviderSelection effective, int attempts) {
        if (status == GmTurnStatus.COMMITTED || status == GmTurnStatus.FAILED
                || status == GmTurnStatus.FAILED_RETRYABLE) {
            throw new IllegalStateException("terminal GM turn cannot transition");
        }
        if (target == GmTurnStatus.PROCESSING && status != GmTurnStatus.STARTED
                || target == GmTurnStatus.COMMITTED && status != GmTurnStatus.PROCESSING
                || (target == GmTurnStatus.FAILED || target == GmTurnStatus.FAILED_RETRYABLE)
                && status != GmTurnStatus.PROCESSING) {
            throw new IllegalStateException("invalid GM turn transition: " + status + " -> " + target);
        }
        return new GmTurn(turnId, commandId, expectedSessionVersion, input, fingerprint, target, nextFailure, nextProvider,
                requested, effective, attempts);
    }

    private static String fingerprint(long expectedVersion, GmInput input) {
        String canonical = switch (input) {
            case GmInput.TextInput text -> expectedVersion + "|TEXT|" + text.text();
            case GmInput.MapActionInput map -> expectedVersion + "|MAP_ACTION|" + map.mapId() + "|" + map.mapVersion() + "|" + map.action();
            case GmInput.MetaQuestionInput question -> expectedVersion + "|META_QUESTION|" + question.question();
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
    public RequestedGmProviderSelection requestedSelection() { return requestedSelection; }
    public EffectiveGmProviderSelection effectiveSelection() { return effectiveSelection; }
    public int attemptCount() { return attemptCount; }
}
