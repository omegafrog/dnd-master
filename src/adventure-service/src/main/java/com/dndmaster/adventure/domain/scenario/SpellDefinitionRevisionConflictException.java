package com.dndmaster.adventure.domain.scenario;

public final class SpellDefinitionRevisionConflictException extends RuntimeException {
    private final long expectedRevision;
    private final long actualRevision;

    public SpellDefinitionRevisionConflictException(long expectedRevision, long actualRevision) {
        super("spell definition revision is stale");
        this.expectedRevision = expectedRevision;
        this.actualRevision = actualRevision;
    }

    public long expectedRevision() { return expectedRevision; }
    public long actualRevision() { return actualRevision; }
}
