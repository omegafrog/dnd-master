package com.dndmaster.adventure.application.quality;

/** State snapshot used to prove a retryable provider/candidate failure is non-mutating. */
public record GoldenJourneyFailureSnapshot(
        long adventureVersion,
        String conversation,
        String stageKey,
        String failureCode) {
    public GoldenJourneyFailureSnapshot(long adventureVersion, String conversation, String stageKey) {
        this(adventureVersion, conversation, stageKey, null);
    }

    public GoldenJourneyFailureSnapshot {
        if (adventureVersion < 0) throw new IllegalArgumentException("adventure version must not be negative");
        conversation = required(conversation, "conversation");
        stageKey = required(stageKey, "stage key");
        if (failureCode != null) failureCode = required(failureCode, "failure code");
    }

    public GoldenJourneyFailureSnapshot failedRetryable(String failureCode) {
        return new GoldenJourneyFailureSnapshot(adventureVersion, conversation, stageKey, failureCode);
    }

    public boolean isImmutableComparedTo(GoldenJourneyFailureSnapshot before) {
        return before != null
                && adventureVersion == before.adventureVersion
                && conversation.equals(before.conversation)
                && stageKey.equals(before.stageKey);
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " required");
        return value.trim();
    }
}
