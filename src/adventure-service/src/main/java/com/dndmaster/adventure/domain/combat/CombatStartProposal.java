package com.dndmaster.adventure.domain.combat;

import java.util.List;
import java.util.Objects;

/** Typed, validated GM proposal consumed by the Adventure Runtime. */
public record CombatStartProposal(boolean accepted, List<CombatParticipant> participants) {
    public CombatStartProposal {
        participants = List.copyOf(Objects.requireNonNull(participants, "combat participants must not be null"));
        if (accepted && participants.isEmpty()) throw new IllegalArgumentException("accepted combat proposal needs participants");
    }
}
