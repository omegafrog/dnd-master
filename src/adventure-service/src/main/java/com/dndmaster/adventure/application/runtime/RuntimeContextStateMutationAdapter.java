package com.dndmaster.adventure.application.runtime;

import com.dndmaster.adventure.domain.adventure.AdventureContext;
import java.util.Objects;
import java.util.Set;

/** Applies deterministic state changes to persisted runtime context. */
public final class RuntimeContextStateMutationAdapter implements AuthoritativeStateMutationPort {
    private static final Set<String> KNOWN_FACT_IDS = Set.of(
            "target.hp", "movement.distance", "turn.advance", "roll.faces", "roll.total",
            "save.success", "save.total", "attack.hit", "attack.total", "damage.total");
    @Override
    public AdventureContext apply(AdventureContext current, AuthoritativeResolution resolution) {
        Objects.requireNonNull(current, "current context must not be null");
        Objects.requireNonNull(resolution, "resolution must not be null");
        if (resolution.status() != AuthoritativeResolution.Status.RESOLVED) {
            throw new IllegalStateException("only resolved outcomes may mutate state");
        }
        if (resolution.referencedFactIds().stream().anyMatch(id -> !KNOWN_FACT_IDS.contains(id.value()))) {
            throw new IllegalStateException("unknown authoritative fact id");
        }
        String state = current.npcState();
        if (!resolution.stateChanges().isEmpty()) {
            String changes = String.join(",", resolution.stateChanges());
            state = (state == null || state.isBlank() ? "" : state + "; ") + "authoritative=" + changes;
        }
        return new AdventureContext(current.currentScene(), state, current.pendingAction(), resolution.outcome());
    }
}
