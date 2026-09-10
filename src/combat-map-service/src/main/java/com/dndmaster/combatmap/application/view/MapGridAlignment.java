package com.dndmaster.combatmap.application.view;

import com.dndmaster.combatmap.domain.MapId;
import java.util.Objects;

/** 원본 이미지 좌표로 저장하는 맵 격자 정렬값. 게임 칸 상태와 분리된다. */
public record MapGridAlignment(MapId mapId, String imageRevision, double originX, double originY, double cellSize, long version) {
    public MapGridAlignment {
        Objects.requireNonNull(mapId, "map id is required");
        if (imageRevision == null || imageRevision.isBlank() || !Double.isFinite(originX) || !Double.isFinite(originY)
                || !Double.isFinite(cellSize) || cellSize <= 0 || version < 0) {
            throw new IllegalArgumentException("invalid map grid alignment");
        }
    }
}
