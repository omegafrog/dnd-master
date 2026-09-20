package com.dndmaster.combatmap.application.movement;

import com.dndmaster.combatmap.domain.GridPosition;
import com.dndmaster.combatmap.domain.MapId;
import com.dndmaster.combatmap.domain.PlayerId;
import com.dndmaster.combatmap.domain.TokenId;
import java.util.List;
import java.util.Objects;

/** 공개된 통행 정보만으로 이동 후보를 계산하기 위한 비영속 요청이다. */
public record MovementPreviewRequest(
        MapId mapId,
        PlayerId playerId,
        TokenId tokenId,
        GridPosition destination,
        List<GridPosition> waypoints,
        String appliedEdition,
        long expectedVersion) {
    public static final int MAX_WAYPOINTS = 16;

    public MovementPreviewRequest {
        Objects.requireNonNull(mapId, "map id must not be null");
        Objects.requireNonNull(playerId, "player id must not be null");
        Objects.requireNonNull(tokenId, "token id must not be null");
        Objects.requireNonNull(destination, "destination must not be null");
        waypoints = waypoints == null ? List.of() : List.copyOf(waypoints);
        if (waypoints.stream().anyMatch(Objects::isNull)) throw new IllegalArgumentException("waypoints must not contain null");
        if (waypoints.size() > MAX_WAYPOINTS) throw new IllegalArgumentException("too many waypoints");
        if (appliedEdition == null || appliedEdition.isBlank()) throw new IllegalArgumentException("applied edition required");
        if (expectedVersion < 0) throw new IllegalArgumentException("expected version must not be negative");
        appliedEdition = appliedEdition.trim();
    }
}
