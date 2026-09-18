package com.dndmaster.combatmap.application.movement;

import com.dndmaster.combatmap.domain.MapId;
import com.dndmaster.combatmap.domain.MovementPath;
import com.dndmaster.combatmap.domain.PlayerId;
import com.dndmaster.combatmap.domain.TokenId;
import java.util.UUID;

public record MovementStartRequest(MapId mapId, PlayerId playerId, TokenId tokenId, MovementPath path,
        String appliedEdition, UUID commandId, String fingerprint, long expectedVersion) { }
