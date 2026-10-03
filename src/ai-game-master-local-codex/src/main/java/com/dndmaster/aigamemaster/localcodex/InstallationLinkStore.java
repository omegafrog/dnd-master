package com.dndmaster.aigamemaster.localcodex;

import java.util.Optional;

/** Stores only whether this client installation is linked; never stores Codex credentials. */
public interface InstallationLinkStore {
    Optional<Boolean> getEnabled();
    void setEnabled(boolean enabled);
    Optional<Boolean> getReauthenticationRequired();
    void setReauthenticationRequired(boolean required);
}
