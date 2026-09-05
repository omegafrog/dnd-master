package com.dndmaster.adventure.application.combat;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Boundary command passed to owning services at the terminal combat commit. */
public record CombatFinalizationCommand(UUID adventureId, UUID encounterId, UUID sessionId,
                                        UUID ownerPlayerId, List<UUID> participantIds) {
    public CombatFinalizationCommand {
        Objects.requireNonNull(adventureId);
        Objects.requireNonNull(encounterId);
        Objects.requireNonNull(sessionId);
        Objects.requireNonNull(ownerPlayerId);
        participantIds = List.copyOf(Objects.requireNonNull(participantIds));
    }
}
