package com.dndmaster.adventure.application.runtime;

import com.dndmaster.adventure.domain.runtime.checkpoint.ContextSummaryCandidate;
import java.util.Objects;

/** Backend contract guard. Provider output cannot alter exact tail or reference another plan. */
public final class ValidatingContextCompactionPort implements ContextCompactionPort {
    private final ContextCompactionPort delegate;
    public ValidatingContextCompactionPort(ContextCompactionPort delegate) { this.delegate = Objects.requireNonNull(delegate); }
    @Override public ContextSummaryCandidate summarize(ContextCompactionRequest request) {
        ContextSummaryCandidate candidate = Objects.requireNonNull(delegate.summarize(request));
        if (!candidate.planRevisionId().equals(request.snapshotReferences().planRevisionId()) || candidate.summary().isBlank()) {
            throw new IllegalArgumentException("invalid context compaction response");
        }
        return candidate;
    }
}
