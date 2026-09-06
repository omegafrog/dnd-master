package com.dndmaster.adventure.domain.combat;

import java.util.List;
import java.util.UUID;

public record InitiativeOrder(List<UUID> participantIds) {
    public InitiativeOrder {
        participantIds = List.copyOf(participantIds);
    }
}
