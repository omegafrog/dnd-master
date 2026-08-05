package com.dndmaster.combatmap.domain;

import java.util.UUID;

public record GameTimeAdvanced(UUID adventureId, long ruleTurn, UUID causeId) {
    public GameTimeAdvanced { if (adventureId == null || causeId == null || ruleTurn < 0) throw new IllegalArgumentException("invalid game time event"); }
}
