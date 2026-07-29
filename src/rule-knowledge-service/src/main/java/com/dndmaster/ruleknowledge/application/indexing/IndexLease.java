package com.dndmaster.ruleknowledge.application.indexing;

import com.dndmaster.ruleknowledge.domain.index.IndexId;
import java.time.Instant;
import java.util.Objects;

public record IndexLease(IndexId indexId, String owner, String token, Instant until) {
    public IndexLease {
        Objects.requireNonNull(indexId);
        if (owner == null || owner.isBlank()) throw new IllegalArgumentException("lease owner must not be blank");
        if (token == null || token.isBlank()) throw new IllegalArgumentException("lease token must not be blank");
        Objects.requireNonNull(until);
    }
}
