package com.dndmaster.adventure.application.runtime;

import java.util.Optional;
import java.util.UUID;

@FunctionalInterface
public interface OfficialToolPort {
    GmToolOutcome execute(GmToolInvocation invocation);
    default Optional<GmToolOutcome> query(UUID commandId) { return Optional.empty(); }
}
