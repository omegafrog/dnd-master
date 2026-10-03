package com.dndmaster.aigamemaster.infrastructure.ai;

import java.time.Duration;

/** Account operations supported by the local Codex app-server adapter. */
public interface CodexAccountClient {
    boolean isAvailable();
    boolean isAuthenticated();
    String startBrowserLogin();
    LoginWaitResult awaitAuthentication(Duration waitTimeout, Duration pollInterval);
}
