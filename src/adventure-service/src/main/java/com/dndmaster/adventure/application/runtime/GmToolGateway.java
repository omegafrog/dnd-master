package com.dndmaster.adventure.application.runtime;

public interface GmToolGateway {
    GmToolOutcome invoke(TurnCapability capability, GmToolInvocation invocation);
}
