package com.dndmaster.adventure.application.combat;

import java.util.List;
import java.util.UUID;

public record CombatMapEnemyMovementCommand(UUID mapId, UUID ownerPlayerId, UUID tokenId,
        List<Position> path, int distance, String appliedEdition, long expectedVersion, UUID commandId) {
    public CombatMapEnemyMovementCommand { path = List.copyOf(path); }
    public record Position(int x, int y) { }
}
