package com.dndmaster.combatmap.application.movement;
import com.dndmaster.combatmap.domain.*; import java.util.Objects;
import java.util.UUID;
import java.util.List;
public record MovePlayerTokenCommand(MapId mapId, PlayerId playerId, TokenId tokenId, MovementPath path, String appliedEdition, UUID commandId,
        long expectedVersion, List<GridPosition> waypoints, String previewFingerprint) {
    public MovePlayerTokenCommand {
        Objects.requireNonNull(mapId); Objects.requireNonNull(playerId); Objects.requireNonNull(tokenId); Objects.requireNonNull(path);
        Objects.requireNonNull(commandId, "command id must not be null");
        if(appliedEdition==null||appliedEdition.isBlank())throw new IllegalArgumentException("applied edition required");
        if(expectedVersion<0)throw new IllegalArgumentException("expected version must not be negative");
        appliedEdition=appliedEdition.trim();
        List<GridPosition> suppliedWaypoints = waypoints == null ? List.of() : waypoints;
        if (suppliedWaypoints.stream().anyMatch(Objects::isNull)) throw new IllegalArgumentException("waypoints must not contain null");
        waypoints = List.copyOf(suppliedWaypoints);
        if (previewFingerprint != null && previewFingerprint.isBlank()) throw new IllegalArgumentException("preview fingerprint must not be blank");
    }
    public MovePlayerTokenCommand(MapId mapId, PlayerId playerId, TokenId tokenId, MovementPath path, String appliedEdition,
            UUID commandId, long expectedVersion) {
        this(mapId, playerId, tokenId, path, appliedEdition, commandId, expectedVersion, List.of(), null);
    }
    public String fingerprint(){
        String base = mapId+"|"+playerId+"|"+tokenId+"|"+path+"|"+appliedEdition+"|"+expectedVersion;
        return previewFingerprint == null ? base : base + "|preview=" + previewFingerprint;
    }
}
