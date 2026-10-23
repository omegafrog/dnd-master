package com.dndmaster.adventure.domain.runtime;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Objects;
import java.util.UUID;

/** Durable identity for a map-owned, all-party token replacement. */
public record CombatTokenTransition(UUID operationId, UUID encounterId, UUID mapId, Direction direction,
        long expectedMapVersion, Status status, java.util.List<Placement> placements) {
    public enum Direction { ENTER, EXIT }
    public enum Status { REQUESTED, PLACEMENT_REQUIRED, APPLIED }
    public record Placement(UUID targetId, int x, int y) {
        public Placement { Objects.requireNonNull(targetId); if (x < 0 || y < 0) throw new IllegalArgumentException("placement coordinates must not be negative"); }
    }
    public CombatTokenTransition(UUID operationId, UUID encounterId, UUID mapId, Direction direction,
            long expectedMapVersion, Status status) {
        this(operationId, encounterId, mapId, direction, expectedMapVersion, status, java.util.List.of());
    }
    @JsonCreator public CombatTokenTransition(@JsonProperty("operationId") UUID operationId,
            @JsonProperty("encounterId") UUID encounterId, @JsonProperty("mapId") UUID mapId,
            @JsonProperty("direction") Direction direction, @JsonProperty("expectedMapVersion") long expectedMapVersion,
            @JsonProperty("status") Status status, @JsonProperty("placements") java.util.List<Placement> placements) {
        this.operationId = Objects.requireNonNull(operationId);
        this.encounterId = Objects.requireNonNull(encounterId);
        this.mapId = Objects.requireNonNull(mapId);
        this.direction = Objects.requireNonNull(direction);
        if (expectedMapVersion < 0) throw new IllegalArgumentException("map version must not be negative");
        this.expectedMapVersion = expectedMapVersion;
        this.status = Objects.requireNonNull(status);
        this.placements = (placements == null ? java.util.stream.Stream.<Placement>empty() : placements.stream())
                .sorted(java.util.Comparator.comparing(Placement::targetId)).toList();
        if (this.placements.stream().map(Placement::targetId).distinct().count() != this.placements.size())
            throw new IllegalArgumentException("placement targets must be unique");
    }
    public CombatTokenTransition withStatus(Status next) {
        return new CombatTokenTransition(operationId, encounterId, mapId, direction, expectedMapVersion, next, placements);
    }
    public CombatTokenTransition withPlacements(java.util.List<Placement> next) {
        return new CombatTokenTransition(operationId, encounterId, mapId, direction, expectedMapVersion,
                Status.REQUESTED, next);
    }
    public CombatTokenTransition withPlacements(java.util.List<Placement> next, long currentMapVersion) {
        return new CombatTokenTransition(operationId, encounterId, mapId, direction, currentMapVersion,
                Status.REQUESTED, next);
    }
}
