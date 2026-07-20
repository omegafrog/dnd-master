package com.dndmaster.combatmap.application.movement;
import com.dndmaster.combatmap.domain.*; import java.util.Objects;
public record MovePlayerTokenCommand(MapId mapId, PlayerId playerId, TokenId tokenId, MovementPath path, String appliedEdition) {
    public MovePlayerTokenCommand { Objects.requireNonNull(mapId); Objects.requireNonNull(playerId); Objects.requireNonNull(tokenId); Objects.requireNonNull(path); if(appliedEdition==null||appliedEdition.isBlank())throw new IllegalArgumentException("applied edition required"); appliedEdition=appliedEdition.trim(); }
}
