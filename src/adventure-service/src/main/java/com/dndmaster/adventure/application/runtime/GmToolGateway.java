package com.dndmaster.adventure.application.runtime;

import java.util.Optional;
import java.util.UUID;

public interface GmToolGateway {
    GmToolOutcome invoke(TurnCapability capability, GmToolInvocation invocation);
    default void revoke(TurnCapability capability) { }
    default Optional<GmToolOutcome> query(String toolName, UUID commandId) { return Optional.empty(); }
}
