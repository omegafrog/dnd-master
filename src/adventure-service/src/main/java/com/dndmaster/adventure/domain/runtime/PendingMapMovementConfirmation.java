package com.dndmaster.adventure.domain.runtime;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Durable Adventure-owned confirmation state for a public map movement preview. */
public record PendingMapMovementConfirmation(
        UUID adventureId,
        UUID ownerPlayerId,
        UUID mapId,
        UUID tokenId,
        long mapVersion,
        List<Position> path,
        int distance,
        String fingerprint,
        List<Position> waypoints) {
    public PendingMapMovementConfirmation {
        Objects.requireNonNull(adventureId, "adventure id must not be null");
        Objects.requireNonNull(ownerPlayerId, "owner player id must not be null");
        Objects.requireNonNull(mapId, "map id must not be null");
        Objects.requireNonNull(tokenId, "token id must not be null");
        if (mapVersion < 0) throw new IllegalArgumentException("map version must not be negative");
        path = copyPositions(path, "path");
        if (path.size() < 2) throw new IllegalArgumentException("movement path must contain at least two positions");
        if (distance < 0) throw new IllegalArgumentException("movement distance must not be negative");
        if (fingerprint == null || fingerprint.isBlank()) throw new IllegalArgumentException("fingerprint must not be blank");
        waypoints = copyPositions(waypoints, "waypoints");
    }

    private static List<Position> copyPositions(List<Position> positions, String name) {
        Objects.requireNonNull(positions, name + " must not be null");
        if (positions.stream().anyMatch(Objects::isNull)) throw new IllegalArgumentException(name + " must not contain null");
        return List.copyOf(positions);
    }

    public record Position(int x, int y) {
        public Position {
            if (x < 0 || y < 0) throw new IllegalArgumentException("movement positions must not be negative");
        }
    }
}
