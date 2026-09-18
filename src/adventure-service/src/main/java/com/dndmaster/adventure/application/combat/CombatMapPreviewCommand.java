package com.dndmaster.adventure.application.combat;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Adventure의 플레이어 이동 검증 경계를 위한 비영속 미리보기 요청이다. */
public record CombatMapPreviewCommand(UUID mapId, UUID ownerPlayerId, UUID tokenId,
        CombatMapPreviewPosition destination, List<CombatMapPreviewPosition> waypoints,
        String appliedEdition, long expectedVersion) {
    public CombatMapPreviewCommand {
        Objects.requireNonNull(mapId, "map id must not be null");
        Objects.requireNonNull(ownerPlayerId, "owner player id must not be null");
        Objects.requireNonNull(tokenId, "token id must not be null");
        Objects.requireNonNull(destination, "destination must not be null");
        waypoints = waypoints == null ? List.of() : List.copyOf(waypoints);
        if (waypoints.stream().anyMatch(Objects::isNull)) throw new IllegalArgumentException("waypoints must not contain null");
        appliedEdition = Objects.requireNonNull(appliedEdition, "applied edition must not be null").trim();
        if (appliedEdition.isBlank()) throw new IllegalArgumentException("applied edition must not be blank");
        if (expectedVersion < 0) throw new IllegalArgumentException("expected map version must be non-negative");
    }
}
