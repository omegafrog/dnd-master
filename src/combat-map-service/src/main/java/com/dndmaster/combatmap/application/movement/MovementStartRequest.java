package com.dndmaster.combatmap.application.movement;

import com.dndmaster.combatmap.domain.MapId;
import com.dndmaster.combatmap.domain.MovementPath;
import com.dndmaster.combatmap.domain.PlayerId;
import com.dndmaster.combatmap.domain.TokenId;
import java.util.UUID;
import java.util.List;
import java.util.Objects;
import com.dndmaster.combatmap.domain.GridPosition;

public record MovementStartRequest(MapId mapId, PlayerId playerId, TokenId tokenId, MovementPath path,
        String appliedEdition, UUID commandId, String fingerprint, String previewFingerprint,
        List<GridPosition> waypoints, long expectedVersion) {
    public MovementStartRequest {
        waypoints = waypoints == null ? List.of() : List.copyOf(waypoints);
        if (waypoints.stream().anyMatch(Objects::isNull)) throw new IllegalArgumentException("waypoints must not contain null");
    }

    public MovementStartRequest(MapId mapId, PlayerId playerId, TokenId tokenId, MovementPath path,
            String appliedEdition, UUID commandId, String fingerprint, long expectedVersion) {
        this(mapId, playerId, tokenId, path, appliedEdition, commandId, fingerprint, null, List.of(), expectedVersion);
    }
}
