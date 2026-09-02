package com.dndmaster.adventure.application.runtime;

import java.util.Optional;
import java.util.UUID;
import java.util.List;

public interface GmToolGateway {
    default List<GmToolSpec> modelTools() { return List.of(); }
    default void preflight(TurnCapability capability, GmToolInvocation invocation) { }
    GmToolOutcome invoke(TurnCapability capability, GmToolInvocation invocation);
    default void revoke(TurnCapability capability) { }
    default Optional<GmToolOutcome> query(String toolName, UUID commandId) { return Optional.empty(); }
}
