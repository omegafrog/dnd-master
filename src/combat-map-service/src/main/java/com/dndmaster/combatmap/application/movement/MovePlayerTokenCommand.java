package com.dndmaster.combatmap.application.movement;
import com.dndmaster.combatmap.domain.*; import java.util.Objects;
import java.util.UUID;
public record MovePlayerTokenCommand(MapId mapId, PlayerId playerId, TokenId tokenId, MovementPath path, String appliedEdition, UUID commandId, long expectedVersion) {
    public MovePlayerTokenCommand { Objects.requireNonNull(mapId); Objects.requireNonNull(playerId); Objects.requireNonNull(tokenId); Objects.requireNonNull(path); Objects.requireNonNull(commandId, "command id must not be null"); if(appliedEdition==null||appliedEdition.isBlank())throw new IllegalArgumentException("applied edition required"); if(expectedVersion<0)throw new IllegalArgumentException("expected version must not be negative"); appliedEdition=appliedEdition.trim(); }
    public String fingerprint(){return mapId+"|"+playerId+"|"+tokenId+"|"+path+"|"+appliedEdition;}
}
