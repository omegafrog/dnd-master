package com.dndmaster.adventure.application.runtime;

import com.dndmaster.adventure.domain.adventure.AdventureContext;
import java.util.Objects;

/** Applies deterministic state changes to persisted runtime context. */
public final class RuntimeContextStateMutationAdapter implements AuthoritativeStateMutationPort {
    @Override
    public AdventureContext apply(AdventureContext current, AuthoritativeResolution resolution) {
        Objects.requireNonNull(current, "current context must not be null");
        Objects.requireNonNull(resolution, "resolution must not be null");
        if (resolution.status() != AuthoritativeResolution.Status.RESOLVED) {
            throw new IllegalStateException("only resolved outcomes may mutate state");
        }
        String state = current.npcState();
        if (!resolution.stateChanges().isEmpty()) {
            String changes = String.join(",", resolution.stateChanges());
            state = (state == null || state.isBlank() ? "" : state + "; ") + "authoritative=" + changes;
        }
        return new AdventureContext(current.currentScene(), state, current.pendingAction(), resolution.outcome());
    }
}
