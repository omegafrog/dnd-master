package com.dndmaster.combatmap.application.movement;

import com.dndmaster.combatmap.application.view.MapOwnerId;
import com.dndmaster.combatmap.domain.MapId;
import com.dndmaster.combatmap.domain.MovementPath;
import com.dndmaster.combatmap.domain.TokenId;
import java.util.Objects;
import java.util.UUID;

public record MoveEnemyTokenCommand(MapId mapId, MapOwnerId owner, TokenId tokenId, MovementPath path,
        String appliedEdition, UUID commandId, long expectedVersion) {
    public MoveEnemyTokenCommand {
        Objects.requireNonNull(mapId);
        Objects.requireNonNull(owner);
        Objects.requireNonNull(tokenId);
        Objects.requireNonNull(path);
        Objects.requireNonNull(commandId);
        if (appliedEdition == null || appliedEdition.isBlank() || expectedVersion < 0)
            throw new IllegalArgumentException("environment movement command is incomplete");
    }
    public String fingerprint() {
        return mapId + "|" + owner + "|ENVIRONMENT_MOVE|" + tokenId + "|" + path + "|" + appliedEdition + "|" + expectedVersion;
    }
}
