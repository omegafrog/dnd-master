package com.dndmaster.adventure.application.runtime;

import com.dndmaster.adventure.domain.runtime.checkpoint.ContextSummaryCandidate;

@FunctionalInterface
public interface ContextCompactionPort {
    ContextSummaryCandidate summarize(ContextCompactionRequest request);
}
