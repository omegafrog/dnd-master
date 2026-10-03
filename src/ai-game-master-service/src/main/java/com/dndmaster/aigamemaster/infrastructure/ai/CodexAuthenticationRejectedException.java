package com.dndmaster.aigamemaster.infrastructure.ai;

/** Safe classification for app-server execution rejected because the account needs authentication. */
public final class CodexAuthenticationRejectedException extends IllegalStateException {
    public CodexAuthenticationRejectedException() {
        super("Codex app-server rejected authentication");
    }
}
