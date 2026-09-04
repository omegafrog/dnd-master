package com.dndmaster.adventure.application.runtime;

import java.util.List;
import java.util.Objects;

/** Typed Runtime GM boundary. Fact access is represented by one composite result list. */
@FunctionalInterface
public interface RuntimeGmAgentPort {
    GmPlanResult continueTurn(RuntimeGmAgentRequest request);

    record RuntimeGmAgentRequest(GmContextEnvelope context, List<RuntimeFactLookupResult> factLookups) {
        public RuntimeGmAgentRequest {
            context = Objects.requireNonNull(context, "context must not be null");
            factLookups = List.copyOf(Objects.requireNonNull(factLookups, "fact lookups must not be null"));
        }
    }
}
